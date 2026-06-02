package com.aidea.aidea.domain.teamspace.dto;

import lombok.*;
import java.time.LocalDateTime;

/**
 * 팀스페이스 요약 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamSpaceSummary {

    private String teamspaceId; // 팀스페이스 ID
    private String name; // 팀스페이스 이름
    private int memberCount; // 멤버 수
    private LocalDateTime createdAt; // 생성일시
}