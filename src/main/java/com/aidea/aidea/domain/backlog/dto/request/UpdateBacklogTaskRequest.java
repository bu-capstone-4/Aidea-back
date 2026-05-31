package com.aidea.aidea.domain.backlog.dto.request;

import com.aidea.aidea.domain.backlog.entity.IssueType;
import com.aidea.aidea.domain.backlog.entity.Priority;
import com.aidea.aidea.domain.backlog.entity.StoryStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record UpdateBacklogTaskRequest(
        @NotBlank String title,
        StoryStatus status,
        Priority priority,
        IssueType issueType,
        String sprint,
        Long assigneeId,
        LocalDate dueDate,
        Long storyId
) {}
