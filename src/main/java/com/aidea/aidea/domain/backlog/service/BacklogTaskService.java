package com.aidea.aidea.domain.backlog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.backlog.dto.request.CreateBacklogTaskRequest;
import com.aidea.aidea.domain.backlog.dto.request.LinkStoryRequest;
import com.aidea.aidea.domain.backlog.dto.request.ReorderRequest;
import com.aidea.aidea.domain.backlog.dto.request.UpdateBacklogTaskRequest;
import com.aidea.aidea.domain.backlog.dto.request.UpdateBacklogTaskStatusRequest;
import com.aidea.aidea.domain.backlog.dto.response.BacklogTaskResponse;
import com.aidea.aidea.domain.backlog.dto.response.ReorderResponse;
import com.aidea.aidea.domain.backlog.entity.BacklogConfig;
import com.aidea.aidea.domain.backlog.entity.Story;
import com.aidea.aidea.domain.backlog.entity.Task;
import com.aidea.aidea.domain.backlog.repository.BacklogConfigRepository;
import com.aidea.aidea.domain.backlog.repository.StoryRepository;
import com.aidea.aidea.domain.backlog.repository.TaskRepository;
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
public class BacklogTaskService {

    private final TaskRepository taskRepository;
    private final StoryRepository storyRepository;
    private final BacklogConfigRepository backlogConfigRepository;
    private final TeamspaceMemberRepository teamspaceMemberRepository;
    private final UserRepository userRepository;
    private final BacklogEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public BacklogTaskResponse createTask(String teamspaceId, Long userId, CreateBacklogTaskRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());
        validateFields(teamspaceId, request.issueType(), request.priority(), request.sprint(), request.dueDate());

        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User assignee = resolveUser(request.assigneeId());

        long nextNumber = taskRepository.findMaxStandaloneNumberByTeamspaceId(teamspaceId) + 1;
        int maxPosition = taskRepository.findStandaloneByTeamspaceId(teamspaceId)
                .stream().mapToInt(Task::getPosition).max().orElse(0) + 1000;

        Task task = Task.createStandalone(teamspaceId, nextNumber, request.title(),
                request.status(), request.priority(), request.issueType(),
                request.sprint(), assignee, reporter, request.dueDate(), maxPosition);
        if (request.storyId() != null) {
            Story linkedStory = resolveLinkedStory(teamspaceId, request.storyId());
            task.linkToStory(linkedStory);
        }
        Task saved = taskRepository.save(task);

        BacklogTaskResponse response = BacklogTaskResponse.from(saved);
        broadcast(teamspaceId, userId, "backlogtask:created", Map.of("task", response));
        return response;
    }

    public BacklogTaskResponse updateTask(String teamspaceId, Long userId, Long taskId,
                                          UpdateBacklogTaskRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());
        validateFields(teamspaceId, request.issueType(), request.priority(), request.sprint(), request.dueDate());

        Task task = getStandaloneTaskInTeamspace(taskId, teamspaceId);
        User assignee = resolveUser(request.assigneeId());
        task.updateStandalone(request.title(), request.status(), request.priority(),
                request.issueType(), request.sprint(), assignee, request.dueDate());

        if (request.storyId() == null) {
            task.unlinkFromStory();
        } else {
            Story linkedStory = resolveLinkedStory(teamspaceId, request.storyId());
            task.linkToStory(linkedStory);
        }

        BacklogTaskResponse response = BacklogTaskResponse.from(task);
        broadcast(teamspaceId, userId, "backlogtask:updated", Map.of("task", response));
        return response;
    }

    public BacklogTaskResponse changeStatus(String teamspaceId, Long userId, Long taskId,
                                            UpdateBacklogTaskStatusRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        Task task = getStandaloneTaskInTeamspace(taskId, teamspaceId);
        task.updateStandaloneStatus(request.status());

        BacklogTaskResponse response = BacklogTaskResponse.from(task);
        broadcast(teamspaceId, userId, "backlogtask:status_changed", Map.of(
                "taskId", taskId, "status", task.getStatus()));
        return response;
    }

    public ReorderResponse reorder(String teamspaceId, Long userId, ReorderRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        List<Long> orderedIds = request.orderedIds();
        List<Task> tasks = taskRepository.findByTeamspaceIdAndStoryIsNullAndIdIn(teamspaceId, orderedIds);

        Map<Long, Task> taskMap = tasks.stream().collect(Collectors.toMap(Task::getId, t -> t));
        for (int i = 0; i < orderedIds.size(); i++) {
            Task task = taskMap.get(orderedIds.get(i));
            if (task != null) {
                task.updatePosition((i + 1) * 1000);
            }
        }
        taskRepository.saveAll(tasks);

        ReorderResponse response = new ReorderResponse(orderedIds);
        broadcast(teamspaceId, userId, "backlogtask:reordered", Map.of("orderedIds", orderedIds));
        return response;
    }

    public BacklogTaskResponse linkStory(String teamspaceId, Long userId, Long taskId,
                                         LinkStoryRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        Task task = getStandaloneTaskInTeamspace(taskId, teamspaceId);

        if (request.storyId() == null) {
            task.unlinkFromStory();
        } else {
            Story linkedStory = resolveLinkedStory(teamspaceId, request.storyId());
            task.linkToStory(linkedStory);
        }

        BacklogTaskResponse response = BacklogTaskResponse.from(task);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("storyId", request.storyId());
        broadcast(teamspaceId, userId, "backlogtask:story_changed", payload);
        return response;
    }

    public void deleteTask(String teamspaceId, Long userId, Long taskId) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        Task task = getStandaloneTaskInTeamspace(taskId, teamspaceId);
        taskRepository.delete(task);

        broadcast(teamspaceId, userId, "backlogtask:deleted", Map.of("taskId", taskId));
    }

    private Task getStandaloneTaskInTeamspace(Long taskId, String teamspaceId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException(ErrorCode.BACKLOG_TASK_NOT_FOUND));
        if (!task.isStandalone() || !teamspaceId.equals(task.getTeamspaceId())) {
            throw new CustomException(ErrorCode.BACKLOG_TASK_NOT_FOUND);
        }
        return task;
    }

    private void validateFields(String teamspaceId, Object issueType, Object priority,
                                 String sprint, Object dueDate) {
        BacklogConfig config = backlogConfigRepository.findById(teamspaceId).orElse(null);
        if (config == null) return;
        if (issueType != null && !config.isFeBeEnabled()) {
            throw new CustomException(ErrorCode.BACKLOG_CONFIG_FIELD_NOT_ALLOWED);
        }
        if (priority != null && !config.isPriorityEnabled()) {
            throw new CustomException(ErrorCode.BACKLOG_CONFIG_FIELD_NOT_ALLOWED);
        }
        if (sprint != null && !config.isSprintEnabled()) {
            throw new CustomException(ErrorCode.BACKLOG_CONFIG_FIELD_NOT_ALLOWED);
        }
        if (dueDate != null && !config.isDueDateEnabled()) {
            throw new CustomException(ErrorCode.BACKLOG_CONFIG_FIELD_NOT_ALLOWED);
        }
    }

    private Story resolveLinkedStory(String teamspaceId, Long storyId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORY_NOT_FOUND));
        if (!story.getTeamspaceId().equals(teamspaceId)) {
            throw new CustomException(ErrorCode.STORY_NOT_FOUND);
        }
        return story;
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
