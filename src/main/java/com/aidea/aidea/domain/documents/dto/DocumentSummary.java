package com.aidea.aidea.domain.documents.dto;

import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DocumentSummary {

    private String id;
    private DocumentType type;
    private String title;
    private LocalDateTime updatedAt;
    private String updatedBy; // User.id (Long) → String

    public static DocumentSummary from(Document document) {
        return new DocumentSummary(
                document.getId(),
                document.getType(),
                document.getTitle(),
                document.getUpdatedAt(),
                document.getUpdatedBy() != null
                        ? document.getUpdatedBy().getId().toString()
                        : null
        );
    }
}
