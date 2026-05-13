package com.aidea.aidea.domain.teamspace.dto;

import lombok.*;

/**
 * 팀스페이스 수정 요청 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamSpaceUpdateRequest {

    private String name; // 수정할 팀스페이스 이름
    private String status; // 수정할 상태
}