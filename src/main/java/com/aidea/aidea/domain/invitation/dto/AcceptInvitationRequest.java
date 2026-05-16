package com.aidea.aidea.domain.invitation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AcceptInvitationRequest {

    @NotBlank(message = "토큰은 필수입니다.")
    private String token;
}
