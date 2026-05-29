package com.aidea.aidea.domain.aifeedback.service;

import com.aidea.aidea.domain.aifeedback.controller.dto.AnswerRequest;
import com.aidea.aidea.domain.aifeedback.controller.dto.FeedbackDetailResponse;
import com.aidea.aidea.domain.aifeedback.controller.dto.FeedbackIdResponse;
import com.aidea.aidea.domain.aifeedback.controller.dto.FeedbackRequest;
import com.aidea.aidea.domain.aifeedback.entity.Answer;
import com.aidea.aidea.domain.aifeedback.entity.Feedback;
import com.aidea.aidea.domain.aifeedback.entity.FeedbackStatus;
import com.aidea.aidea.domain.aifeedback.repository.FeedbackRepository;
import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentAiStatus;
import com.aidea.aidea.domain.documents.entity.DocumentUpdate;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.documents.repository.DocumentUpdateRepository;
import com.aidea.aidea.global.util.YjsTextExtractor;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import com.aidea.aidea.global.util.TeamspaceRoleValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final DocumentRepository documentRepository;
    private final DocumentUpdateRepository documentUpdateRepository;
    private final UserRepository userRepository;
    private final GeminiService geminiService;
    private final FeedbackEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final TeamspaceRoleValidator roleValidator;

    private static final List<FeedbackStatus> IN_PROGRESS_STATUSES = List.of(
            FeedbackStatus.PENDING,
            FeedbackStatus.QUESTIONING,
            FeedbackStatus.ANSWERING,
            FeedbackStatus.DONE
    );

    @Transactional
    public FeedbackIdResponse initiateFeedback(String docId, FeedbackRequest request, Long userId) {
        Document document = documentRepository.findById(docId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        roleValidator.requireRole(document.getTeamspace().getTeamspaceId(), userId, MemberRole.OWNER, MemberRole.MEMBER);

        if (document.getStatus() == DocumentAiStatus.DRAFT) {
            throw new CustomException(ErrorCode.DRAFT_IN_PROGRESS);
        }

        boolean inProgress = feedbackRepository.existsByDocumentIdAndStatusIn(docId, IN_PROGRESS_STATUSES);
        if (inProgress) {
            throw new CustomException(ErrorCode.FEEDBACK_IN_PROGRESS);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // Yjs 바이너리 → 텍스트 디코딩
        byte[] snapshot = document.getYjsSnapshot();
        List<byte[]> updates = documentUpdateRepository.findByDocumentIdOrderByIdAsc(docId)
                .stream().map(DocumentUpdate::getUpdateBinary).toList();
        String originalMarkdown = YjsTextExtractor.extractText(snapshot, updates);
        if (originalMarkdown.isBlank()) {
            originalMarkdown = "# " + document.getTitle();
        }
        log.debug("[FEEDBACK] extractedText docId={} length={}", docId, originalMarkdown.length());

        Feedback feedback = Feedback.create(
                UUID.randomUUID().toString(),
                document,
                user,
                originalMarkdown,
                request.additionalRequest()
        );
        feedbackRepository.save(feedback);

        publishStartedEvent(feedback);

        String feedbackId = feedback.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                geminiService.callGemini(feedbackId);
            }
        });

        return new FeedbackIdResponse(feedback.getId(), feedback.getStatus());
    }

    @Transactional
    public FeedbackIdResponse submitAnswer(String feedbackId, AnswerRequest request, Long userId) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new CustomException(ErrorCode.FEEDBACK_NOT_FOUND));

        roleValidator.requireRole(feedback.getDocument().getTeamspace().getTeamspaceId(), userId, MemberRole.OWNER, MemberRole.MEMBER);

        if (feedback.getStatus() != FeedbackStatus.QUESTIONING) {
            throw new CustomException(ErrorCode.FEEDBACK_INVALID_STATUS);
        }

        List<Answer> answers = request.answer().stream()
                .map(item -> new Answer(item.questionId(), item.value()))
                .collect(Collectors.toList());

        feedback.setAnswers(answers);
        feedback.setStatus(FeedbackStatus.ANSWERING);

        String fId = feedback.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                geminiService.callGeminiWithAnswers(fId);
            }
        });

        return new FeedbackIdResponse(feedback.getId(), feedback.getStatus());
    }

    @Transactional
    public FeedbackIdResponse acceptFeedback(String feedbackId, Long userId) {
        Feedback feedback = findAndAuthorize(feedbackId, userId);

        if (feedback.getStatus() != FeedbackStatus.DONE) {
            throw new CustomException(ErrorCode.FEEDBACK_INVALID_STATUS);
        }

        feedback.setStatus(FeedbackStatus.ACCEPTED);
        publishResolvedEvent(feedback, "ACCEPTED");

        return new FeedbackIdResponse(feedback.getId(), feedback.getStatus());
    }

    @Transactional
    public FeedbackIdResponse rejectFeedback(String feedbackId, Long userId) {
        Feedback feedback = findAndAuthorize(feedbackId, userId);

        if (feedback.getStatus() != FeedbackStatus.DONE) {
            throw new CustomException(ErrorCode.FEEDBACK_INVALID_STATUS);
        }

        feedback.setStatus(FeedbackStatus.REJECTED);
        publishResolvedEvent(feedback, "REJECTED");

        return new FeedbackIdResponse(feedback.getId(), feedback.getStatus());
    }

    public FeedbackDetailResponse getFeedback(String feedbackId, Long userId) {
        Feedback feedback = findAndAuthorize(feedbackId, userId);
        return FeedbackDetailResponse.from(feedback);
    }

    private Feedback findAndAuthorize(String feedbackId, Long userId) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new CustomException(ErrorCode.FEEDBACK_NOT_FOUND));

        roleValidator.requireRole(feedback.getDocument().getTeamspace().getTeamspaceId(), userId, MemberRole.OWNER, MemberRole.MEMBER);

        return feedback;
    }

    private void publishStartedEvent(Feedback feedback) {
        try {
            String eventJson = objectMapper.writeValueAsString(Map.of(
                    "type", "feedback:started",
                    "feedbackId", feedback.getId(),
                    "requestedBy", Map.of(
                            "userId", feedback.getRequestedBy().getId(),
                            "name", feedback.getRequestedBy().getName()
                    )
            ));
            eventPublisher.publishToDocument(feedback.getDocument().getId(), eventJson);
        } catch (Exception e) {
            log.error("feedback:started 푸시 실패", e);
        }
    }

    private void publishResolvedEvent(Feedback feedback, String outcome) {
        try {
            String eventJson = objectMapper.writeValueAsString(Map.of(
                    "type", "feedback:resolved",
                    "feedbackId", feedback.getId(),
                    "outcome", outcome
            ));
            eventPublisher.publishToDocument(feedback.getDocument().getId(), eventJson);
        } catch (Exception e) {
            log.error("feedback:resolved 푸시 실패", e);
        }
    }
}