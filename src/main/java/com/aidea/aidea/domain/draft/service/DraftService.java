package com.aidea.aidea.domain.draft.service;

import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentAiStatus;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.draft.controller.dto.DraftAnswerRequest;
import com.aidea.aidea.domain.draft.controller.dto.DraftAnswerResponse;
import com.aidea.aidea.domain.draft.entity.Draft;
import com.aidea.aidea.domain.draft.entity.DraftAnswer;
import com.aidea.aidea.domain.draft.entity.DraftStatus;
import com.aidea.aidea.domain.draft.repository.DraftRepository;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import com.aidea.aidea.global.util.TeamspaceRoleValidator;
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
public class DraftService {

    private final DraftRepository draftRepository;
    private final DocumentRepository documentRepository;
    private final DraftAsyncExecutor draftAsyncExecutor;
    private final TeamspaceRoleValidator roleValidator;

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

    @Transactional
    public DraftAnswerResponse submitDraftAnswer(String draftId, DraftAnswerRequest request, Long userId) {
        Draft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new CustomException(ErrorCode.DRAFT_NOT_FOUND));

        Document document = draft.getDocument();
        String teamspaceId = document.getTeamspace().getTeamspaceId();
        roleValidator.requireRole(teamspaceId, userId, MemberRole.OWNER, MemberRole.MEMBER);

        if (draft.getStatus() != DraftStatus.QUESTIONING) {
            throw new CustomException(ErrorCode.DRAFT_INVALID_STATUS);
        }

        // answer를 비워서 보내면 "건너뛰기" — Q&A 없이 원본 설명만으로 바로 최종 생성으로 진행
        List<DraftAnswer> answers = (request.answer() == null) ? List.of()
                : request.answer().stream()
                        .map(item -> new DraftAnswer(item.questionId(), item.value()))
                        .toList();

        draft.setAnswers(answers);
        draft.setStatus(DraftStatus.ANSWERING);
        log.warn("[DRAFT] ANSWERING 전환 draftId={} answerCount={}", draftId, answers.size());

        String dId = draft.getId();
        String teamspaceName = document.getTeamspace().getName();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.warn("[DRAFT] afterCommit fired - scheduling final IDEA generation draftId={}", dId);
                draftAsyncExecutor.generateFinalIdeaDraft(dId, teamspaceId, teamspaceName);
            }
        });

        return DraftAnswerResponse.from(draft);
    }
}