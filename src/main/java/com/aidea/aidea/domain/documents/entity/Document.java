package com.aidea.aidea.domain.documents.entity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "teamspace_id", nullable = false)
    private String teamspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private DocumentType type;

    @Column(name = "title", nullable = false)
    private String title;

    @Lob
    @Column(name = "yjs_binary", nullable = false)
    private byte[] yjsBinary;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;
}