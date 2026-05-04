package com.aidea.aidea.domain.documents.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_updates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentUpdate {

    // AUTO_INCREMENT 자동증가 — 수신 순서 보장 핵심
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    // Y.encodeStateAsUpdate() 결과 바이너리
    @Column(name = "update_binary", columnDefinition = "LONGBLOB", nullable = false)
    private byte[] updateBinary;

    // 변경을 발생시킨 유저/탭 식별자 (userId|tabId)
    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static DocumentUpdate create(Document document, byte[] updateBinary, String clientId) {
        DocumentUpdate u = new DocumentUpdate();
        u.document = document;
        u.updateBinary = updateBinary;
        u.clientId = clientId;
        u.createdAt = LocalDateTime.now();
        return u;
    }
}
