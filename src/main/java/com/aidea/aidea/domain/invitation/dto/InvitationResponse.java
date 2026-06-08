package com.aidea.aidea.domain.invitation.dto;

import com.aidea.aidea.domain.invitation.entity.Invitation;
import lombok.Getter;

@Getter
public class InvitationResponse {

    private final String invitationId;
    private final String email;
    private final String role;

    public InvitationResponse(Invitation invitation) {
        this.invitationId = invitation.getId();
        this.email = invitation.getInviteeEmail();
        this.role = invitation.getRole() != null ? invitation.getRole().name() : "MEMBER";
    }
}
