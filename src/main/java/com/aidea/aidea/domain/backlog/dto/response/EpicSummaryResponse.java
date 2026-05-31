package com.aidea.aidea.domain.backlog.dto.response;

import com.aidea.aidea.domain.backlog.entity.Epic;

public record EpicSummaryResponse(
        Long id,
        String name,
        String color
) {
    public static EpicSummaryResponse from(Epic epic) {
        return new EpicSummaryResponse(epic.getId(), epic.getName(), epic.getColor());
    }
}
