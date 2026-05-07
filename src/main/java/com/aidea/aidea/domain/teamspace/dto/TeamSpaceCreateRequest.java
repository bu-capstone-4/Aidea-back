package com.aidea.aidea.domain.teamspace.dto;

import lombok.*;

import java.util.List;

import com.aidea.aidea.domain.documents.entity.DocumentType;

/**
 * 팀스페이스 생성 요청 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamSpaceCreateRequest {

    private String name; // 팀스페이스 이름

    private String idea; // 입력 아이디어 (AI 초안 생성용)

    private List<DocumentType> documentTypes; // 생성할 문서 타입 목록 (IDEA, PLAN, USER_SCENARIO, API_SPEC, ERD)
}