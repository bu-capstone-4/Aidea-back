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
    public void triggerDraftGeneration(String documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        if (draftRepository.existsByDocumentIdAndStatus(documentId, DraftStatus.PENDING)) {
            return;
        }

        document.setStatus(DocumentAiStatus.DRAFT);

        Draft draft = Draft.create(UUID.randomUUID().toString(), document);
        draftRepository.save(draft);

        String draftId = draft.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                draftAsyncExecutor.generateDraftAsync(draftId);
            }
        });
    }
}