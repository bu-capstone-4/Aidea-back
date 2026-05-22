package com.aidea.aidea.domain.backlog.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateTaskRequest(
        @NotBlank String title,
        Long assigneeId
) {}
