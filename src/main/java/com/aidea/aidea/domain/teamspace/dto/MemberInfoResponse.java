package com.aidea.aidea.domain.teamspace.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberInfoResponse {
    private Long userId;
    private String name;
    private String email;
    private String role;
    private String status;
    private String profileImageUrl;
    private String invitationId;
}
