package com.aidea.aidea.domain.invitation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BulkInviteResultItem {

    private String email;
    private String status; // SENT, ALREADY_MEMBER
}
