package com.aidea.aidea.domain.backlog.dto.request;

import com.aidea.aidea.domain.backlog.entity.EpicStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateEpicStatusRequest(
        @NotNull EpicStatus status
) {}
