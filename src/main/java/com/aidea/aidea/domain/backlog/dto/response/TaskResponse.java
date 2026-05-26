package com.aidea.aidea.domain.backlog.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.aidea.aidea.domain.backlog.entity.IssueType;
import com.aidea.aidea.domain.backlog.entity.Task;

import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        String title,
        IssueType issueType,
        @JsonProperty("isCompleted") boolean isCompleted,
        UserResponse assignee,
        int position,
        LocalDateTime createdAt
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getIssueType(),
                task.isCompleted(),
                task.getAssignee() != null ? UserResponse.from(task.getAssignee()) : null,
                task.getPosition(),
                task.getCreatedAt()
        );
    }
}
