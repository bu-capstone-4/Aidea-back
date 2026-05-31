package com.aidea.aidea.domain.backlog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.backlog.dto.request.CreateEpicRequest;
import com.aidea.aidea.domain.backlog.dto.request.ReorderRequest;
import com.aidea.aidea.domain.backlog.dto.request.UpdateEpicRequest;
import com.aidea.aidea.domain.backlog.dto.request.UpdateEpicStatusRequest;
import com.aidea.aidea.domain.backlog.dto.response.EpicResponse;
import com.aidea.aidea.domain.backlog.dto.response.ReorderResponse;
import com.aidea.aidea.domain.backlog.entity.BacklogConfig;
import com.aidea.aidea.domain.backlog.entity.Epic;
import com.aidea.aidea.domain.backlog.repository.BacklogConfigRepository;
import com.aidea.aidea.domain.backlog.repository.EpicRepository;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.domain.teamspace.entity.TeamspaceMember;
import com.aidea.aidea.domain.teamspace.repository.TeamspaceMemberRepository;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class EpicService {

    private final EpicRepository epicRepository;
    private final BacklogConfigRepository backlogConfigRepository;
    private final TeamspaceMemberRepository teamspaceMemberRepository;
    private final UserRepository userRepository;
    private final BacklogEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<EpicResponse> getEpics(String teamspaceId, Long userId) {
        getMemberOrThrow(teamspaceId, userId);
        List<Epic> epics = epicRepository.findAllWithCreatorByTeamspaceId(teamspaceId);
        Map<Long, int[]> storyCounts = buildStoryCounts(teamspaceId);
        return epics.stream().map(e -> toResponse(e, storyCounts)).toList();
    }

    public EpicResponse createEpic(String teamspaceId, Long userId, CreateEpicRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());
        validateEpicEnabled(teamspaceId);
        validateEpicFields(teamspaceId, request.issueType(), request.priority(), request.dueDate());

        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User assignee = resolveUser(request.assigneeId());

        long nextNumber = epicRepository.findMaxNumberByTeamspaceId(teamspaceId) + 1;
        int maxPosition = epicRepository.findAllWithCreatorByTeamspaceId(teamspaceId)
                .stream().mapToInt(Epic::getPosition).max().orElse(0) + 1000;

        Epic epic = Epic.create(teamspaceId, nextNumber, request.name(), request.color(),
                request.description(), request.priority(), request.issueType(),
                assignee, creator, request.dueDate(), maxPosition);
        Epic saved = epicRepository.save(epic);

        EpicResponse response = EpicResponse.from(saved);
        broadcast(teamspaceId, userId, "epic:created", Map.of("epic", response));
        return response;
    }

    public EpicResponse updateEpic(String teamspaceId, Long userId, Long epicId, UpdateEpicRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());
        validateEpicFields(teamspaceId, request.issueType(), request.priority(), request.dueDate());

        Epic epic = epicRepository.findById(epicId)
                .orElseThrow(() -> new CustomException(ErrorCode.EPIC_NOT_FOUND));
        validateBelongsToTeamspace(epic, teamspaceId);

        User assignee = resolveUser(request.assigneeId());
        epic.update(request.name(), request.color(), request.description(),
                request.priority(), request.issueType(), assignee, request.dueDate());

        EpicResponse response = EpicResponse.from(epic);
        broadcast(teamspaceId, userId, "epic:updated", Map.of("epic", response));
        return response;
    }

    public EpicResponse changeStatus(String teamspaceId, Long userId, Long epicId, UpdateEpicStatusRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        Epic epic = epicRepository.findById(epicId)
                .orElseThrow(() -> new CustomException(ErrorCode.EPIC_NOT_FOUND));
        validateBelongsToTeamspace(epic, teamspaceId);

        epic.updateStatus(request.status());

        EpicResponse response = EpicResponse.from(epic);
        broadcast(teamspaceId, userId, "epic:status_changed", Map.of(
                "epicId", epicId,
                "status", epic.getStatus(),
                "closedAt", epic.getClosedAt() != null ? epic.getClosedAt().toString() : null));
        return response;
    }

    public ReorderResponse reorder(String teamspaceId, Long userId, ReorderRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        List<Long> orderedIds = request.orderedIds();
        List<Epic> epics = epicRepository.findByTeamspaceIdAndIdIn(teamspaceId, orderedIds);

        Map<Long, Epic> epicMap = epics.stream().collect(Collectors.toMap(Epic::getId, e -> e));
        for (int i = 0; i < orderedIds.size(); i++) {
            Epic epic = epicMap.get(orderedIds.get(i));
            if (epic != null) {
                epic.updatePosition((i + 1) * 1000);
            }
        }
        epicRepository.saveAll(epics);

        ReorderResponse response = new ReorderResponse(orderedIds);
        broadcast(teamspaceId, userId, "epic:reordered", Map.of("orderedIds", orderedIds));
        return response;
    }

    public void deleteEpic(String teamspaceId, Long userId, Long epicId) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        Epic epic = epicRepository.findById(epicId)
                .orElseThrow(() -> new CustomException(ErrorCode.EPIC_NOT_FOUND));
        validateBelongsToTeamspace(epic, teamspaceId);

        epicRepository.delete(epic);
        broadcast(teamspaceId, userId, "epic:deleted", Map.of("epicId", epicId));
    }

    private Map<Long, int[]> buildStoryCounts(String teamspaceId) {
        Map<Long, int[]> counts = new HashMap<>();
        epicRepository.findStoryCountsByTeamspaceId(teamspaceId).forEach(row -> counts.put(
                (Long) row[0],
                new int[]{((Number) row[1]).intValue(), ((Number) row[2]).intValue()}
        ));
        return counts;
    }

    private EpicResponse toResponse(Epic epic, Map<Long, int[]> storyCounts) {
        int[] counts = storyCounts.getOrDefault(epic.getId(), new int[]{0, 0});
        return EpicResponse.from(epic, counts[0], counts[1]);
    }

    private void validateEpicEnabled(String teamspaceId) {
        BacklogConfig config = backlogConfigRepository.findById(teamspaceId).orElse(null);
        if (config != null && !config.isEpicEnabled()) {
            throw new CustomException(ErrorCode.BACKLOG_CONFIG_FIELD_NOT_ALLOWED);
        }
    }

    private void validateEpicFields(String teamspaceId, Object issueType, Object priority, Object dueDate) {
        BacklogConfig config = backlogConfigRepository.findById(teamspaceId).orElse(null);
        if (config == null) return;
        if (issueType != null && !config.isFeBeEnabled()) {
            throw new CustomException(ErrorCode.BACKLOG_CONFIG_FIELD_NOT_ALLOWED);
        }
        if (priority != null && !config.isPriorityEnabled()) {
            throw new CustomException(ErrorCode.BACKLOG_CONFIG_FIELD_NOT_ALLOWED);
        }
        if (dueDate != null && !config.isDueDateEnabled()) {
            throw new CustomException(ErrorCode.BACKLOG_CONFIG_FIELD_NOT_ALLOWED);
        }
    }

    private User resolveUser(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
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

    private void validateBelongsToTeamspace(Epic epic, String teamspaceId) {
        if (!epic.getTeamspaceId().equals(teamspaceId)) {
            throw new CustomException(ErrorCode.EPIC_NOT_FOUND);
        }
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
