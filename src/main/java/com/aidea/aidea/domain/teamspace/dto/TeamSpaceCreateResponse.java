package com.aidea.aidea.domain.teamspace.dto;

import lombok.*;
import java.time.LocalDateTime;

/**
 * 팀스페이스 생성 응답 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamSpaceCreateResponse {

    private String teamspaceId; // 팀스페이스 ID
    private String name; // 팀스페이스 이름
    private String status; // 상태 (CREATING, CREATED)
    private LocalDateTime createdAt; // 생성일시
}