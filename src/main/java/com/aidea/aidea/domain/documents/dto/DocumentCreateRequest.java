package com.aidea.aidea.domain.documents.dto;

import com.aidea.aidea.domain.documents.entity.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DocumentCreateRequest {

    @NotBlank(message = "팀스페이스 ID는 필수입니다")
    private String teamspaceId;

    @NotNull(message = "문서 타입은 필수입니다")
    private DocumentType type;

    @Size(max = 100, message = "제목은 100자를 초과할 수 없습니다")
    private String title; // 없으면 서버에서 type.name() 으로 기본값 설정
}
