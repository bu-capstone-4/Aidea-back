package com.aidea.aidea.domain.documents.dto;

import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;


/*
    문서 요약 응답 dto

*/


@Getter
@AllArgsConstructor
public class DocumentSummary {

    private String id;
    private DocumentType type;
    private String title;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public static DocumentSummary from(Document document) {
        return new DocumentSummary(
                document.getId(),
                document.getType(),
                document.getTitle(),
                document.getUpdatedAt(),
                document.getUpdatedBy()
        );
    }
}