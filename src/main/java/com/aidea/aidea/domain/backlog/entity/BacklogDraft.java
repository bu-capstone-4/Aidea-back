package com.aidea.aidea.domain.backlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "backlog_drafts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BacklogDraft {

    @Id
    private String id; // UUID

    @Column(nullable = false, unique = true)
    private String teamspaceId;

    @Setter
    @Enumerated(EnumType.STRING)
    private BacklogDraftStatus status;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public static BacklogDraft create(String id, String teamspaceId) {
        BacklogDraft draft = new BacklogDraft();
        draft.id = id;
        draft.teamspaceId = teamspaceId;
        draft.status = BacklogDraftStatus.PENDING;
        return draft;
    }

    public void markDone() {
        this.status = BacklogDraftStatus.DONE;
    }

    public void markFailed(String errorCode, String errorMessage) {
        this.status = BacklogDraftStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public void markPending() {
        this.status = BacklogDraftStatus.PENDING;
        this.errorCode = null;
        this.errorMessage = null;
    }
}
