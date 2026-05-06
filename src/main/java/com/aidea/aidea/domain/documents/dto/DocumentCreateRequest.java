package com.aidea.aidea.domain.documents.dto;

import com.aidea.aidea.domain.documents.entity.DocumentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@NoArgsConstructor
public class DocumentCreateRequest {

    @NotBlank
    private String teamspaceId;

    @NotNull
    private DocumentType type;

    // 선택값 (없으면 서버에서 기본값 생성)
    private String title;
    
}