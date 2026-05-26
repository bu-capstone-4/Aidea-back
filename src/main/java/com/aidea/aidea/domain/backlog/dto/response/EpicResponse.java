package com.aidea.aidea.domain.backlog.dto.response;

import com.aidea.aidea.domain.backlog.entity.Epic;
import com.aidea.aidea.domain.backlog.entity.EpicStatus;
import com.aidea.aidea.domain.backlog.entity.IssueType;
import com.aidea.aidea.domain.backlog.entity.Priority;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EpicResponse(
        Long id,
        Long number,
        String name,
        String color,
        String description,
        EpicStatus status,
        Priority priority,
        IssueType issueType,
        UserResponse assignee,
        UserResponse reporter,
        LocalDate dueDate,
        int position,
        int storyCount,
        int completedStoryCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime closedAt
) {
    public static EpicResponse from(Epic epic, int storyCount, int completedStoryCount) {
        return new EpicResponse(
                epic.getId(),
                epic.getNumber(),
                epic.getName(),
                epic.getColor(),
                epic.getDescription(),
                epic.getStatus(),
                epic.getPriority(),
                epic.getIssueType(),
                epic.getAssignee() != null ? UserResponse.from(epic.getAssignee()) : null,
                UserResponse.from(epic.getCreatedBy()),
                epic.getDueDate(),
                epic.getPosition(),
                storyCount,
                completedStoryCount,
                epic.getCreatedAt(),
                epic.getUpdatedAt(),
                epic.getClosedAt()
        );
    }

    public static EpicResponse from(Epic epic) {
        return from(epic, 0, 0);
    }
}
