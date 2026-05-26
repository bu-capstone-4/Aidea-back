package com.aidea.aidea.domain.backlog.dto.response;

import com.aidea.aidea.domain.backlog.entity.IssueType;
import com.aidea.aidea.domain.backlog.entity.Priority;
import com.aidea.aidea.domain.backlog.entity.Story;
import com.aidea.aidea.domain.backlog.entity.StoryStatus;
import com.aidea.aidea.domain.backlog.entity.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record StorySummaryResponse(
        Long id,
        Long number,
        String title,
        StoryStatus status,
        Priority priority,
        IssueType issueType,
        String sprint,
        List<EpicSummaryResponse> epics,
        UserResponse assignee,
        UserResponse reporter,
        int taskCount,
        int completedTaskCount,
        LocalDate dueDate,
        int position,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static StorySummaryResponse from(Story story) {
        List<Task> tasks = story.getTasks();
        return new StorySummaryResponse(
                story.getId(),
                story.getNumber(),
                story.getTitle(),
                story.getStatus(),
                story.getPriority(),
                story.getIssueType(),
                story.getSprint(),
                story.getStoryEpics().stream()
                        .map(se -> EpicSummaryResponse.from(se.getEpic()))
                        .toList(),
                story.getAssignee() != null ? UserResponse.from(story.getAssignee()) : null,
                UserResponse.from(story.getReporter()),
                tasks.size(),
                (int) tasks.stream().filter(Task::isCompleted).count(),
                story.getDueDate(),
                story.getPosition(),
                story.getCreatedAt(),
                story.getUpdatedAt()
        );
    }
}
