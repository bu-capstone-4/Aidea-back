package com.aidea.aidea.domain.draft.entity;

import com.aidea.aidea.domain.documents.entity.Document;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "drafts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Draft {

    @Id
    private String id;  //UUID

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Setter
    @Enumerated(EnumType.STRING)
    private DraftStatus status;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String content; //Ai 생성 마크다운 (성공 시)

    @Setter
    @Column(name = "error_message")
    private String errorMessage; //실패 시 사유

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static Draft create(String id, Document document) {
        Draft draft = new Draft();
        draft.id = id;
        draft.document = document;
        draft.status = DraftStatus.PENDING;
        draft.createdAt = LocalDateTime.now();
        return draft;
    }
}