package com.aidea.aidea.domain.documents.dto;


import com.aidea.aidea.domain.documents.entity.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DocumentUpdateResponse {

    private String id;
    private String title;

    public static DocumentUpdateResponse from(Document document) {
        return new DocumentUpdateResponse(
                document.getId(),
                document.getTitle()
        );
    }
}