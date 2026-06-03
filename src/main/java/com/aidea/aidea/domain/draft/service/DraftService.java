package com.aidea.aidea.domain.draft.service;

import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentAiStatus;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.draft.entity.Draft;
import com.aidea.aidea.domain.draft.entity.DraftStatus;
import com.aidea.aidea.domain.draft.repository.DraftRepository;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DraftService {

    private final DraftRepository draftRepository;
    private final DocumentRepository documentRepository;
    private final DraftAsyncExecutor draftAsyncExecutor;

    @Transactional
    public void savePendingDraft(String documentId, String ideaContext) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        if (draftRepository.existsByDocumentIdAndStatus(documentId, DraftStatus.PENDING)) {
            return;
        }

        document.setStatus(DocumentAiStatus.DRAFT);

        Draft draft = Draft.create(UUID.randomUUID().toString(), document, ideaContext);
        draftRepository.save(draft);
        log.warn("[DRAFT] pending draft saved draftId={} documentId={}", draft.getId(), documentId);
    }

    @Transactional
    public void triggerDraftGeneration(String documentId, String ideaContext, String teamspaceName) {
        log.warn("[DRAFT] triggerDraftGeneration called documentId={}", documentId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        if (draftRepository.existsByDocumentIdAndStatus(documentId, DraftStatus.PENDING)) {
            log.warn("[DRAFT] skip - already PENDING documentId={}", documentId);
            return;
        }

        document.setStatus(DocumentAiStatus.DRAFT);

        Draft draft = Draft.create(UUID.randomUUID().toString(), document, ideaContext);
        draftRepository.save(draft);
        log.warn("[DRAFT] draft saved draftId={} documentId={}", draft.getId(), documentId);

        String draftId = draft.getId();
        String teamspaceId = document.getTeamspace().getTeamspaceId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.warn("[DRAFT] afterCommit fired - scheduling async draftId={} teamspaceId={}", draftId, teamspaceId);
                draftAsyncExecutor.generateDraftAsync(draftId, teamspaceId, teamspaceName);
            }
        });
        log.warn("[DRAFT] afterCommit synchronization registered draftId={}", draftId);
    }
}