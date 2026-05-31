package com.aidea.aidea.domain.backlog.dto.request;

import com.aidea.aidea.domain.backlog.entity.StoryStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStoryStatusRequest(
        @NotNull StoryStatus status
) {}
