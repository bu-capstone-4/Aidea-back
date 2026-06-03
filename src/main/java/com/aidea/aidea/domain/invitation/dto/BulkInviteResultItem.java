package com.aidea.aidea.domain.invitation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
public class BulkInviteResultItem {

    private final String email;
    private final String status; // SENT, ALREADY_MEMBER

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String invitationId;

    public BulkInviteResultItem(String email, String status, String invitationId) {
        this.email = email;
        this.status = status;
        this.invitationId = invitationId;
    }
}
