package com.aidea.aidea.domain.backlog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.backlog.dto.request.*;
import com.aidea.aidea.domain.backlog.dto.response.*;
import com.aidea.aidea.domain.backlog.entity.*;
import com.aidea.aidea.domain.backlog.repository.EpicRepository;
import com.aidea.aidea.domain.backlog.repository.StoryRepository;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.domain.teamspace.entity.TeamspaceMember;
import com.aidea.aidea.domain.teamspace.repository.TeamspaceMemberRepository;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class StoryService {

    private final StoryRepository storyRepository;
    private final EpicRepository epicRepository;
    private final TeamspaceMemberRepository teamspaceMemberRepository;
    private final UserRepository userRepository;
    private final BacklogEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<StorySummaryResponse> getStories(
            String teamspaceId, Long userId,
            List<StoryStatus> statuses, Long epicId, Long assigneeId, Priority priority) {

        getMemberOrThrow(teamspaceId, userId);
        List<Story> stories = storyRepository.findAllWithRelationsByTeamspaceId(teamspaceId);

        return stories.stream()
                .filter(s -> statuses == null || statuses.isEmpty() || statuses.contains(s.getStatus()))
                .filter(s -> epicId == null || s.getStoryEpics().stream()
                        .anyMatch(se -> se.getEpic().getId().equals(epicId)))
                .filter(s -> assigneeId == null ||
                        (s.getAssignee() != null && s.getAssignee().getId().equals(assigneeId)))
                .filter(s -> priority == null || priority.equals(s.getPriority()))
                .map(StorySummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public StoryDetailResponse getStory(String teamspaceId, Long userId, Long storyId) {
        getMemberOrThrow(teamspaceId, userId);
        Story story = storyRepository.findDetailByIdAndTeamspaceId(storyId, teamspaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORY_NOT_FOUND));
        return StoryDetailResponse.from(story);
    }

    public StoryDetailResponse createStory(String teamspaceId, Long userId, CreateStoryRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        long nextNumber = storyRepository.findMaxNumberByTeamspaceId(teamspaceId) + 1;
        int maxPosition = storyRepository.findAllWithRelationsByTeamspaceId(teamspaceId)
                .stream().mapToInt(Story::getPosition).max().orElse(0) + 1000;

        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User assignee = request.assigneeId() != null
                ? userRepository.findById(request.assigneeId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND))
                : null;
        List<Epic> epics = resolveEpics(teamspaceId, request.epicIds());

        Story story = Story.create(nextNumber, teamspaceId, request.title(), request.body(),
                request.priority(), assignee, reporter, request.dueDate(), maxPosition);
        epics.forEach(epic -> story.getStoryEpics().add(StoryEpic.create(story, epic)));
        Story saved = storyRepository.save(story);

        StoryDetailResponse response = StoryDetailResponse.from(saved);
        broadcast(teamspaceId, userId, "story:created", Map.of("story", StorySummaryResponse.from(saved)));
        return response;
    }

    public StoryDetailResponse updateStory(String teamspaceId, Long userId, Long storyId, UpdateStoryRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        Story story = storyRepository.findDetailByIdAndTeamspaceId(storyId, teamspaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORY_NOT_FOUND));

        User assignee = request.assigneeId() != null
                ? userRepository.findById(request.assigneeId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND))
                : null;
        List<Epic> epics = resolveEpics(teamspaceId, request.epicIds());

        story.update(request.title(), request.body(), story.getStatus(),
                request.priority(), assignee, request.dueDate());
        story.getStoryEpics().clear();
        epics.forEach(epic -> story.getStoryEpics().add(StoryEpic.create(story, epic)));

        StoryDetailResponse response = StoryDetailResponse.from(story);
        broadcast(teamspaceId, userId, "story:updated", Map.of("story", StorySummaryResponse.from(story)));
        return response;
    }

    public StoryStatusResponse changeStatus(String teamspaceId, Long userId, Long storyId,
                                            UpdateStoryStatusRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORY_NOT_FOUND));
        validateBelongsToTeamspace(story, teamspaceId);

        story.updateStatus(request.status());

        StoryStatusResponse response = new StoryStatusResponse(story.getId(), story.getStatus(), story.getClosedAt());
        broadcast(teamspaceId, userId, "story:status_changed", Map.of(
                "storyId", storyId, "status", story.getStatus(), "closedAt",
                story.getClosedAt() != null ? story.getClosedAt().toString() : null));
        return response;
    }

    public ReorderResponse reorder(String teamspaceId, Long userId, ReorderRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        List<Long> orderedIds = request.orderedIds();
        List<Story> stories = storyRepository.findByTeamspaceIdAndIdIn(teamspaceId, orderedIds);

        Map<Long, Story> storyMap = stories.stream()
                .collect(Collectors.toMap(Story::getId, s -> s));
        for (int i = 0; i < orderedIds.size(); i++) {
            Story story = storyMap.get(orderedIds.get(i));
            if (story != null) {
                story.updatePosition((i + 1) * 1000);
            }
        }
        storyRepository.saveAll(stories);

        ReorderResponse response = new ReorderResponse(orderedIds);
        broadcast(teamspaceId, userId, "story:reordered", Map.of("orderedIds", orderedIds));
        return response;
    }

    public void deleteStory(String teamspaceId, Long userId, Long storyId) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORY_NOT_FOUND));
        validateBelongsToTeamspace(story, teamspaceId);

        storyRepository.delete(story);
        broadcast(teamspaceId, userId, "story:deleted", Map.of("storyId", storyId));
    }

    private TeamspaceMember getMemberOrThrow(String teamspaceId, Long userId) {
        return teamspaceMemberRepository.findByTeamspaceIdAndUserId(teamspaceId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TEAMSPACE_MEMBER));
    }

    private void requireWritePermission(MemberRole role) {
        if (role == MemberRole.VIEWER) {
            throw new CustomException(ErrorCode.INSUFFICIENT_PERMISSION);
        }
    }

    private void validateBelongsToTeamspace(Story story, String teamspaceId) {
        if (!story.getTeamspaceId().equals(teamspaceId)) {
            throw new CustomException(ErrorCode.STORY_NOT_FOUND);
        }
    }

    private List<Epic> resolveEpics(String teamspaceId, List<Long> epicIds) {
        if (epicIds == null || epicIds.isEmpty()) return List.of();
        List<Epic> epics = epicRepository.findAllById(epicIds);
        epics.forEach(e -> {
            if (!e.getTeamspaceId().equals(teamspaceId)) {
                throw new CustomException(ErrorCode.EPIC_NOT_FOUND);
            }
        });
        return epics;
    }

    private void broadcast(String teamspaceId, Long actorId, String type, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("actorId", String.valueOf(actorId));
        payload.putAll(extra);
        try {
            eventPublisher.publishToTeamspace(teamspaceId, String.valueOf(actorId),
                    objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
