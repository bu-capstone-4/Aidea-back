package com.aidea.aidea.domain.documents.dto;

import java.time.LocalDateTime;

import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentType;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DocumentCreateResponse {

    private String id;
    private DocumentType type;
    private String title;
    private LocalDateTime createdAt;

    public static DocumentCreateResponse from(Document document) {
        return new DocumentCreateResponse(
                document.getId(),
                document.getType(),
                document.getTitle(),
                document.getCreatedAt()
        );
    }
}