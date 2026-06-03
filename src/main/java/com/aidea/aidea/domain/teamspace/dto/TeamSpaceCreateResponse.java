package com.aidea.aidea.domain.teamspace.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamSpaceCreateResponse {

    private String teamspaceId;
    private String name;
    private LocalDateTime createdAt;
    private List<DocumentInfo> documents;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DocumentInfo {
        private String id;
        private String type;
        private String title;
        private String aiStatus;
    }
}