package com.aidea.aidea.domain.documents.dto;

import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DocumentDetail {

    private String id;
    private String teamspaceId;
    private DocumentType type;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String updatedBy; // User.id (Long) → String

    public static DocumentDetail from(Document document) {
        return new DocumentDetail(
                document.getId(),
                document.getTeamspace().getTeamspaceId(),
                document.getType(),
                document.getTitle(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getUpdatedBy() != null
                        ? document.getUpdatedBy().getId().toString()
                        : null
        );
    }
}
