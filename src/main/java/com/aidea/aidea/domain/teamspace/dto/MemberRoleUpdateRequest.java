package com.aidea.aidea.domain.teamspace.dto;

import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import jakarta.validation.constraints.NotNull;

public record MemberRoleUpdateRequest(
        @NotNull MemberRole role
) {
}
