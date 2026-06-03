package com.aidea.aidea.domain.teamspace.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 팀스페이스 목록 조회 응답 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamSpaceListResponse {

    private List<TeamSpaceSummary> teamspaces; // 팀스페이스 리스트

    /**
     * 팀스페이스 요약 DTO
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TeamSpaceSummary {

        private String teamspaceId; // ID
        private String name; // 이름
        private LocalDateTime createdAt; // 생성일시
    }
}