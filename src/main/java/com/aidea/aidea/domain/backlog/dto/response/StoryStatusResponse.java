package com.aidea.aidea.domain.backlog.dto.response;

import com.aidea.aidea.domain.backlog.entity.StoryStatus;

import java.time.LocalDateTime;

public record StoryStatusResponse(
        Long id,
        StoryStatus status,
        LocalDateTime closedAt
) {}
