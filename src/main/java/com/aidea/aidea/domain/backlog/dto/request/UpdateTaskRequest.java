package com.aidea.aidea.domain.backlog.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateTaskRequest(
        @NotBlank String title,
        Long assigneeId
) {}
