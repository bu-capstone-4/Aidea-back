package com.aidea.aidea.domain.backlog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateEpicRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color,
        String description
) {}
