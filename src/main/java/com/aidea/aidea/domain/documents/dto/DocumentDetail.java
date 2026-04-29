package com.aidea.aidea.domain.documents.dto;

import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/*
    /api/documnets/{documnetid}
    
    문서 상세 조회 응답  dto

*/

@Getter
@AllArgsConstructor
public class DocumentDetail {

    private String id;
    private String teamspaceId;
    private DocumentType type;
    private String title;
    private byte[] yjsBinary; // Base64 문자열, 자바에서는 byte[]로 처리
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public static DocumentDetail from(Document document) {
        return new DocumentDetail(
                document.getId(),
                document.getTeamspaceId(),
                document.getType(),
                document.getTitle(),
                document.getYjsBinary(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getUpdatedBy()
        );
    }
}