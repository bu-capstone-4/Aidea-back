package com.aidea.aidea.domain.backlog.dto.request;

import com.aidea.aidea.domain.backlog.entity.IssueType;
import com.aidea.aidea.domain.backlog.entity.Priority;
import com.aidea.aidea.domain.backlog.entity.StoryStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

public record CreateStoryRequest(
        @NotBlank String title,
        String body,
        StoryStatus status,
        Priority priority,
        IssueType issueType,
        String sprint,
        List<Long> epicIds,
        Long assigneeId,
        LocalDate dueDate
) {}
