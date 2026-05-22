package com.aidea.aidea.domain.backlog.dto.response;

import com.aidea.aidea.domain.backlog.entity.Epic;

import java.time.LocalDateTime;

public record EpicResponse(
        Long id,
        String name,
        String color,
        String description,
        LocalDateTime createdAt,
        UserResponse createdBy
) {
    public static EpicResponse from(Epic epic) {
        return new EpicResponse(
                epic.getId(),
                epic.getName(),
                epic.getColor(),
                epic.getDescription(),
                epic.getCreatedAt(),
                UserResponse.from(epic.getCreatedBy())
        );
    }
}
