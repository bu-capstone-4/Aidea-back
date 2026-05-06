package com.aidea.aidea.domain.teamspace.dto;

import lombok.*;

/**
 * 팀스페이스 생성 요청 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamSpaceCreateRequest {

    private String teamspaceId; // 팀스페이스 ID (PK)
    private String name; // 팀스페이스 이름
}