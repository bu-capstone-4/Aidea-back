package com.aidea.aidea.domain.backlog.dto.response;

import com.aidea.aidea.domain.backlog.entity.IssueType;
import com.aidea.aidea.domain.backlog.entity.Priority;
import com.aidea.aidea.domain.backlog.entity.Story;
import com.aidea.aidea.domain.backlog.entity.StoryStatus;
import com.aidea.aidea.domain.backlog.entity.Task;

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
        IssueType issueType,
        String sprint,
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
        return from(story, story.getTasks());
    }

    public static StoryDetailResponse from(Story story, List<Task> tasks) {
        return new StoryDetailResponse(
                story.getId(),
                story.getNumber(),
                story.getTitle(),
                story.getBody(),
                story.getStatus(),
                story.getPriority(),
                story.getIssueType(),
                story.getSprint(),
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
                tasks.stream()
                        .map(TaskResponse::from)
                        .toList()
        );
    }
}
