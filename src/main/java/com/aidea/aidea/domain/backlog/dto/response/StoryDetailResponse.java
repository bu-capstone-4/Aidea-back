package com.aidea.aidea.domain.backlog.dto.response;

import com.aidea.aidea.domain.backlog.entity.Priority;
import com.aidea.aidea.domain.backlog.entity.Story;
import com.aidea.aidea.domain.backlog.entity.StoryStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record StoryDetailResponse(
        Long id,
        Long number,
        String title,
        String body,
        StoryStatus status,
        Priority priority,
        List<EpicSummaryResponse> epics,
        UserResponse assignee,
        UserResponse reporter,
        LocalDate dueDate,
        int position,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime closedAt,
        List<TaskResponse> tasks
) {
    public static StoryDetailResponse from(Story story) {
        return new StoryDetailResponse(
                story.getId(),
                story.getNumber(),
                story.getTitle(),
                story.getBody(),
                story.getStatus(),
                story.getPriority(),
                story.getStoryEpics().stream()
                        .map(se -> EpicSummaryResponse.from(se.getEpic()))
                        .toList(),
                story.getAssignee() != null ? UserResponse.from(story.getAssignee()) : null,
                UserResponse.from(story.getReporter()),
                story.getDueDate(),
                story.getPosition(),
                story.getCreatedAt(),
                story.getUpdatedAt(),
                story.getClosedAt(),
                story.getTasks().stream()
                        .map(TaskResponse::from)
                        .toList()
        );
    }
}
