package com.aidea.aidea.domain.backlog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.backlog.dto.request.CreateTaskRequest;
import com.aidea.aidea.domain.backlog.dto.request.ReorderRequest;
import com.aidea.aidea.domain.backlog.dto.request.UpdateTaskRequest;
import com.aidea.aidea.domain.backlog.dto.response.ReorderResponse;
import com.aidea.aidea.domain.backlog.dto.response.TaskResponse;
import com.aidea.aidea.domain.backlog.entity.Story;
import com.aidea.aidea.domain.backlog.entity.Task;
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
public class TaskService {

    private final TaskRepository taskRepository;
    private final StoryRepository storyRepository;
    private final TeamspaceMemberRepository teamspaceMemberRepository;
    private final UserRepository userRepository;
    private final BacklogEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public TaskResponse createTask(String teamspaceId, Long userId, Long storyId, CreateTaskRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        Story story = getStoryInTeamspace(storyId, teamspaceId);
        int maxPosition = story.getTasks().stream()
                .mapToInt(Task::getPosition).max().orElse(0) + 1000;

        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User assignee = request.assigneeId() != null
                ? userRepository.findById(request.assigneeId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND))
                : null;

        Task task = Task.create(story, request.title(), assignee, maxPosition, creator);
        Task saved = taskRepository.save(task);

        TaskResponse response = TaskResponse.from(saved);
        broadcast(teamspaceId, userId, "task:created", Map.of("storyId", storyId, "task", response));
        return response;
    }

    public TaskResponse updateTask(String teamspaceId, Long userId, Long storyId, Long taskId,
                                   UpdateTaskRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        getStoryInTeamspace(storyId, teamspaceId);
        Task task = getTaskInStory(taskId, storyId);

        User assignee = request.assigneeId() != null
                ? userRepository.findById(request.assigneeId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND))
                : null;
        task.update(request.title(), task.isCompleted(), assignee);

        TaskResponse response = TaskResponse.from(task);
        broadcast(teamspaceId, userId, "task:updated", Map.of("storyId", storyId, "task", response));
        return response;
    }

    public Map<String, Object> toggleComplete(String teamspaceId, Long userId, Long storyId, Long taskId) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        getStoryInTeamspace(storyId, teamspaceId);
        Task task = getTaskInStory(taskId, storyId);
        task.toggleCompleted();

        Map<String, Object> result = Map.of("id", task.getId(), "isCompleted", task.isCompleted());
        broadcast(teamspaceId, userId, "task:completed", Map.of(
                "storyId", storyId, "taskId", taskId, "isCompleted", task.isCompleted()));
        return result;
    }

    public ReorderResponse reorder(String teamspaceId, Long userId, Long storyId, ReorderRequest request) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        getStoryInTeamspace(storyId, teamspaceId);
        List<Long> orderedIds = request.orderedIds();
        List<Task> tasks = taskRepository.findByStoryIdAndIdIn(storyId, orderedIds);

        Map<Long, Task> taskMap = tasks.stream().collect(Collectors.toMap(Task::getId, t -> t));
        for (int i = 0; i < orderedIds.size(); i++) {
            Task task = taskMap.get(orderedIds.get(i));
            if (task != null) {
                task.updatePosition((i + 1) * 1000);
            }
        }
        taskRepository.saveAll(tasks);

        ReorderResponse response = new ReorderResponse(orderedIds);
        broadcast(teamspaceId, userId, "task:reordered", Map.of("storyId", storyId, "orderedIds", orderedIds));
        return response;
    }

    public void deleteTask(String teamspaceId, Long userId, Long storyId, Long taskId) {
        TeamspaceMember member = getMemberOrThrow(teamspaceId, userId);
        requireWritePermission(member.getRole());

        getStoryInTeamspace(storyId, teamspaceId);
        Task task = getTaskInStory(taskId, storyId);
        taskRepository.delete(task);

        broadcast(teamspaceId, userId, "task:deleted", Map.of("storyId", storyId, "taskId", taskId));
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

    private Story getStoryInTeamspace(Long storyId, String teamspaceId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORY_NOT_FOUND));
        if (!story.getTeamspaceId().equals(teamspaceId)) {
            throw new CustomException(ErrorCode.STORY_NOT_FOUND);
        }
        return story;
    }

    private Task getTaskInStory(Long taskId, Long storyId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException(ErrorCode.TASK_NOT_FOUND));
        if (!task.getStory().getId().equals(storyId)) {
            throw new CustomException(ErrorCode.TASK_NOT_FOUND);
        }
        return task;
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
