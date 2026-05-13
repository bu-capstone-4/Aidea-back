package com.aidea.aidea.domain.aifeedback.service;

import com.aidea.aidea.domain.aifeedback.controller.dto.FeedbackIdResponse;
import com.aidea.aidea.domain.aifeedback.controller.dto.FeedbackRequest;
import com.aidea.aidea.domain.aifeedback.entity.Answer;
import com.aidea.aidea.domain.aifeedback.entity.Feedback;
import com.aidea.aidea.domain.aifeedback.entity.FeedbackStatus;
import com.aidea.aidea.domain.aifeedback.repository.FeedbackRepository;
import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.coyote.BadRequestException;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final UserRepository userRepository;
    private final TeamspaceMemberRepository teamspaceMemberRepository;
    private final GeminiService geminiService;
    private final FeedbackEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // 진행 중인 피드백으로 간주할 상태들 (동시 요청 차단용)
    private static final List<FeedbackStatus> IN_PROGRESS_STATUSES = List.of(
            FeedbackStatus.PENDING,
            FeedbackStatus.QUESTIONING,
            FeedbackStatus.ANSWERING,
            FeedbackStatus.DONE
    );

    @Transactional
    public FeedbackIdResponse initiateFeedback(
            String docId,
            FeedbackRequest request,
            Long userId
    ) {
        // ─ 가드 1: 문서 존재 ─
        Document document = documentRepository.findById(docId)
                .orElseThrow(() -> new ChangeSetPersister.NotFoundException("문서를 찾을 수 없습니다"));

        // ─ 가드 2: 권한 검증 (MEMBER 이상) ─
        requireRole(document.getTeamspace().getId(), userId,
                MemberRole.OWNER, MemberRole.MEMBER);

        // ─ 가드 3: 동시 요청 차단 ─
        boolean inProgress = feedbackRepository
                .existsByDocumentIdAndStatusIn(docId, IN_PROGRESS_STATUSES);
        if (inProgress) {
            throw new ConflictException("이미 진행 중인 피드백이 있습니다");
        }

        // ─ 처리 1: feedback row 생성 + DB INSERT ─
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ChangeSetPersister.NotFoundException("사용자를 찾을 수 없습니다"));

        Feedback feedback = Feedback.create(
                UUID.randomUUID().toString(),
                document,
                user,
                request.originalMarkdown(),
                request.additionalRequest()
        );
        feedbackRepository.save(feedback);

        // ─ 처리 2: WebSocket "feedback:started" 푸시 (락 ON 신호) ─
        publishStartedEvent(feedback);

        // ─ 처리 3: GeminiService 비동기 호출 (트리거만, 결과 안 기다림) ─
        geminiService.callGemini(feedback.getId());

        return new FeedbackIdResponse(feedback.getId(), feedback.getStatus());

        @Transactional
        public FeedbackIdResponse submitAnswer(
                String feedbackId,
                AnswerRequest request,
                Long userId
    ) {
            // ─ 가드 1: 피드백 존재 ─
            Feedback feedback = feedbackRepository.findById(feedbackId)
                    .orElseThrow(() -> new NotFoundException("피드백을 찾을 수 없습니다"));

            // ─ 가드 2: 권한 (해당 팀스페이스 멤버여야 함) ─
            requireRole(feedback.getDocument().getTeamspace().getId(), userId,
                    MemberRole.OWNER, MemberRole.MEMBER);

            // ─ 가드 3: 상태 전이 가드 ─
            if (feedback.getStatus() != FeedbackStatus.QUESTIONING) {
                throw new BadRequestException(
                        "답변 제출은 QUESTIONING 상태에서만 가능합니다. 현재: " + feedback.getStatus()
                );
            }

            // ─ 처리 1: AnswerRequest.AnswerItem → Answer 변환 ─
            List<Answer> answers = request.answers().stream()
                    .map(item -> new Answer(item.questionId(), item.value()))
                    .collect(Collectors.toList());

            // ─ 처리 2: 상태 전이 (더티 체킹으로 자동 UPDATE) ─
            feedback.setAnswers(answers);
            feedback.setStatus(FeedbackStatus.ANSWERING);

            // ─ 처리 3: Gemini 재호출 (비동기) ─
            geminiService.callGeminiWithAnswers(feedback.getId());

            return new FeedbackIdResponse(feedback.getId(), feedback.getStatus());
        }

        @Transactional
        public FeedbackIdResponse acceptFeedback(String feedbackId, Long userId) {
            Feedback feedback = findAndAuthorize(feedbackId, userId);

            // 상태 전이 가드: DONE에서만 가능
            if (feedback.getStatus() != FeedbackStatus.DONE) {
                throw new BadRequestException(
                        "수락은 DONE 상태에서만 가능합니다. 현재: " + feedback.getStatus()
                );
            }

            feedback.setStatus(FeedbackStatus.ACCEPTED);
            publishResolvedEvent(feedback, "ACCEPTED");

            return new FeedbackIdResponse(feedback.getId(), feedback.getStatus());
        }

        @Transactional
        public FeedbackIdResponse rejectFeedback(String feedbackId, Long userId) {
            Feedback feedback = findAndAuthorize(feedbackId, userId);

            if (feedback.getStatus() != FeedbackStatus.DONE) {
                throw new BadRequestException(
                        "거부는 DONE 상태에서만 가능합니다. 현재: " + feedback.getStatus()
                );
            }

            feedback.setStatus(FeedbackStatus.REJECTED);
            publishResolvedEvent(feedback, "REJECTED");

            return new FeedbackIdResponse(feedback.getId(), feedback.getStatus());
        }

        public FeedbackDetailResponse getFeedback(String feedbackId, Long userId) {
            Feedback feedback = findAndAuthorize(feedbackId, userId);
            return FeedbackDetailResponse.from(feedback);
        }

        // 권한 확인 + 피드백 조회 (3개 메서드에서 중복 사용)
        private Feedback findAndAuthorize(String feedbackId, Long userId) {
            Feedback feedback = feedbackRepository.findById(feedbackId)
                    .orElseThrow(() -> new NotFoundException("피드백을 찾을 수 없습니다"));

            requireRole(feedback.getDocument().getTeamspace().getId(), userId,
                    MemberRole.OWNER, MemberRole.MEMBER);

            return feedback;
        }

        // 팀스페이스 권한 체크 (Phase 1 패턴과 동일)
        private void requireRole(String teamspaceId, Long userId, MemberRole... allowedRoles) {
            var member = teamspaceMemberRepository
                    .findByTeamspaceIdAndUserId(teamspaceId, userId)
                    .orElseThrow(() -> new ForbiddenException("팀스페이스 소속이 아닙니다"));

            boolean allowed = false;
            for (MemberRole role : allowedRoles) {
                if (member.getRole() == role) { allowed = true; break; }
            }
            if (!allowed) {
                throw new ForbiddenException("권한이 없습니다");
            }
        }

        // feedback:started 이벤트 푸시
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
                // 푸시 실패해도 메인 흐름 중단 안 함 (가능한 한 견고하게)
            }
        }

        // feedback:resolved 이벤트 푸시
        private void publishResolvedEvent(Feedback feedback, String outcome) {
            try {
                String eventJson = objectMapper.writeValueAsString(Map.of(
                        "type", "feedback:resolved",
                        "feedbackId", feedback.getId(),
                        "outcome", outcome   // "ACCEPTED" or "REJECTED"
                ));
                eventPublisher.publishToDocument(feedback.getDocument().getId(), eventJson);
            } catch (Exception e) {
                log.error("feedback:resolved 푸시 실패", e);
            }
        }
    }
}
