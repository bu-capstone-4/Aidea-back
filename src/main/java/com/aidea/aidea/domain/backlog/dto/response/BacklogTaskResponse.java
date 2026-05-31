package com.aidea.aidea.domain.backlog.dto.response;

import com.aidea.aidea.domain.backlog.entity.IssueType;
import com.aidea.aidea.domain.backlog.entity.Priority;
import com.aidea.aidea.domain.backlog.entity.StoryStatus;
import com.aidea.aidea.domain.backlog.entity.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BacklogTaskResponse(
        Long id,
        Long number,
        String title,
        StoryStatus status,
        Priority priority,
        IssueType issueType,
        String sprint,
        UserResponse assignee,
        UserResponse reporter,
        LocalDate dueDate,
        int position,
        Long storyId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BacklogTaskResponse from(Task task) {
        return new BacklogTaskResponse(
                task.getId(),
                task.getNumber(),
                task.getTitle(),
                task.getStatus(),
                task.getPriority(),
                task.getIssueType(),
                task.getSprint(),
                task.getAssignee() != null ? UserResponse.from(task.getAssignee()) : null,
                task.getReporter() != null ? UserResponse.from(task.getReporter()) : null,
                task.getDueDate(),
                task.getPosition(),
                task.getLinkedStoryId(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
