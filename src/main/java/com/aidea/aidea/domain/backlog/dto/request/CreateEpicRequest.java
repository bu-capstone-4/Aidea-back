package com.aidea.aidea.domain.backlog.dto.request;

import com.aidea.aidea.domain.backlog.entity.IssueType;
import com.aidea.aidea.domain.backlog.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record CreateEpicRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color,
        String description,
        Priority priority,
        IssueType issueType,
        Long assigneeId,
        LocalDate dueDate
) {}
