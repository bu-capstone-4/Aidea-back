package com.aidea.aidea.domain.backlog.dto.request;

import com.aidea.aidea.domain.backlog.entity.Priority;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

public record CreateStoryRequest(
        @NotBlank String title,
        String body,
        Priority priority,
        List<Long> epicIds,
        Long assigneeId,
        LocalDate dueDate
) {}
