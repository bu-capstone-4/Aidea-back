package com.aidea.aidea.domain.backlog.service;

import com.aidea.aidea.domain.backlog.entity.BacklogConfig;
import com.aidea.aidea.domain.backlog.entity.BacklogDraft;
import com.aidea.aidea.domain.backlog.entity.BacklogDraftStatus;
import com.aidea.aidea.domain.backlog.repository.BacklogDraftRepository;
import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentAiStatus;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.teamspace.entity.TeamSpace;
import com.aidea.aidea.domain.teamspace.repository.TeamSpaceRepository;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacklogDraftService {

    private final BacklogDraftRepository backlogDraftRepository;
    private final DocumentRepository documentRepository;
    private final TeamSpaceRepository teamSpaceRepository;
    private final BacklogDraftAsyncExecutor backlogDraftAsyncExecutor;

    @Transactional
    public BacklogDraftStatus triggerBacklogDraftGeneration(String teamspaceId, Long userId, BacklogConfig config) {
        List<Document> documents = documentRepository.findByTeamspaceId(teamspaceId);

        boolean anyDocGenerating = documents.stream()
                .anyMatch(d -> d.getStatus() == DocumentAiStatus.DRAFT);
        if (anyDocGenerating) {
            throw new CustomException(ErrorCode.BACKLOG_DRAFT_BLOCKED_BY_DOCUMENT_DRAFT);
        }

        if (documents.isEmpty()) {
            throw new CustomException(ErrorCode.BACKLOG_DRAFT_NO_PLANNING_DOCUMENT);
        }

        if (backlogDraftRepository.existsByTeamspaceIdAndStatus(teamspaceId, BacklogDraftStatus.PENDING)) {
            throw new CustomException(ErrorCode.BACKLOG_DRAFT_ALREADY_IN_PROGRESS);
        }

        BacklogDraft draft = BacklogDraft.create(UUID.randomUUID().toString(), teamspaceId);
        backlogDraftRepository.save(draft);

        TeamSpace teamSpace = teamSpaceRepository.findById(teamspaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAMSPACE_NOT_FOUND));
        String teamspaceName = teamSpace.getName();
        String draftId = draft.getId();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.warn("[BACKLOG_DRAFT] afterCommit fired - scheduling async draftId={} teamspaceId={}", draftId, teamspaceId);
                backlogDraftAsyncExecutor.generateBacklogDraftAsync(draftId, teamspaceId, teamspaceName, config, userId);
            }
        });

        return BacklogDraftStatus.PENDING;
    }
}
