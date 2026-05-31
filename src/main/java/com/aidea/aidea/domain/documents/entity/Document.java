package com.aidea.aidea.domain.documents.entity;

import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.teamspace.entity.TeamSpace;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teamspace_id", nullable = false)
    private TeamSpace teamspace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType type;

    @Enumerated(EnumType.STRING)
    private DocumentAiStatus status;

    @Column(nullable = false)
    private String title;

    // Yjs 최종 스냅샷 — 배치 머지 전까지 null
    @Column(name = "yjs_snapshot", columnDefinition = "LONGBLOB")
    private byte[] yjsSnapshot;

    // 마지막 머지 시점의 Yjs 논리 시계값
    @Column(name = "snapshot_clock")
    private Integer snapshotClock;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    public static Document create(String id, TeamSpace teamspace, DocumentType type, String title) {
        Document doc = new Document();
        doc.id = id;
        doc.teamspace = teamspace;
        doc.type = type;
        doc.title = title;
        doc.createdAt = LocalDateTime.now();
        doc.updatedAt = LocalDateTime.now();
        return doc;
    }
}
