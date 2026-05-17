package com.aidea.aidea.domain.aifeedback.controller;

import com.aidea.aidea.domain.aifeedback.controller.dto.AnswerRequest;
import com.aidea.aidea.domain.aifeedback.controller.dto.FeedbackDetailResponse;
import com.aidea.aidea.domain.aifeedback.controller.dto.FeedbackIdResponse;
import com.aidea.aidea.domain.aifeedback.controller.dto.FeedbackRequest;
import com.aidea.aidea.domain.aifeedback.service.FeedbackService;
import com.aidea.aidea.global.dto.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Feedback")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "AI 피드백 요청")
    @PostMapping("/documents/{docId}/feedback")
    public ResponseEntity<GlobalResponse<FeedbackIdResponse>> requestFeedback(
            @PathVariable String docId,
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal String userIdStr) {

        Long userId = Long.parseLong(userIdStr);
        FeedbackIdResponse response = feedbackService.initiateFeedback(docId, request, userId);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(GlobalResponse.ok("AI 피드백을 생성하고 있습니다.", response));
    }

    @Operation(summary = "AI 피드백 상태 조회 (폴링용)")
    @GetMapping("/feedbacks/{feedbackId}")
    public ResponseEntity<GlobalResponse<FeedbackDetailResponse>> getFeedback(
            @PathVariable String feedbackId,
            @AuthenticationPrincipal String userIdStr) {

        Long userId = Long.parseLong(userIdStr);
        FeedbackDetailResponse response = feedbackService.getFeedback(feedbackId, userId);

        return ResponseEntity.ok(GlobalResponse.ok(response));
    }

    @Operation(summary = "질문 답변 제출")
    @PostMapping("/feedbacks/{feedbackId}/answers")
    public ResponseEntity<GlobalResponse<FeedbackIdResponse>> submitAnswer(
            @PathVariable String feedbackId,
            @Valid @RequestBody AnswerRequest request,
            @AuthenticationPrincipal String userIdStr) {

        Long userId = Long.parseLong(userIdStr);
        FeedbackIdResponse response = feedbackService.submitAnswer(feedbackId, request, userId);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(GlobalResponse.ok("답변을 처리하고 있습니다.", response));
    }

    @Operation(summary = "AI 피드백 수락 — 수정안 채택")
    @PostMapping("/feedbacks/{feedbackId}/accept")
    public ResponseEntity<GlobalResponse<FeedbackIdResponse>> acceptFeedback(
            @PathVariable String feedbackId,
            @AuthenticationPrincipal String userIdStr) {

        Long userId = Long.parseLong(userIdStr);
        FeedbackIdResponse response = feedbackService.acceptFeedback(feedbackId, userId);

        return ResponseEntity.ok(GlobalResponse.ok("피드백이 적용되었습니다.", response));
    }

    @Operation(summary = "AI 피드백 거부 — 원본 유지")
    @PostMapping("/feedbacks/{feedbackId}/reject")
    public ResponseEntity<GlobalResponse<FeedbackIdResponse>> rejectFeedback(
            @PathVariable String feedbackId,
            @AuthenticationPrincipal String userIdStr) {

        Long userId = Long.parseLong(userIdStr);
        FeedbackIdResponse response = feedbackService.rejectFeedback(feedbackId, userId);

        return ResponseEntity.ok(GlobalResponse.ok("원본이 유지되었습니다.", response));
    }
}