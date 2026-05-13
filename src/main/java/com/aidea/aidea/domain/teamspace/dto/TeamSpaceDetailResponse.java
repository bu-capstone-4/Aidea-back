package com.aidea.aidea.domain.teamspace.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 팀스페이스 단건 조회 응답 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamSpaceDetailResponse {

    private String teamspaceId; // 팀스페이스 ID
    private String name; // 이름
    private String status; // 상태
    private LocalDateTime createdAt; // 생성일시

    private List<DocumentSummary> documents; // 문서 요약 리스트

    /**
     * 문서 요약 DTO (내부 클래스)
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DocumentSummary {

        private String id; // 문서 ID
        private String type; // 문서 타입 (IDEA, PLAN ...)
        private String title; // 제목
        private LocalDateTime updatedAt; // 수정일
        private String updatedBy; // 수정자
    }
}