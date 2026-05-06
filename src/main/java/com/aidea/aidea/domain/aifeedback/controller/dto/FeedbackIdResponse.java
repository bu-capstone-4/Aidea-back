package com.aidea.aidea.domain.aifeedback.controller.dto;

import com.aidea.aidea.domain.aifeedback.entity.FeedbackStatus;

public record FeedbackIdResponse(
        String feedbackId,
        FeedbackStatus status
) {
    //Entity -> DTO 변환 정적 팩토리
    public static FeedbackIdResponse from(com.aidea.aidea.domain.aifeedback.entity.Feedback feedback) {
        return new FeedbackIdResponse(feedback.getId(), feedback.getStatus());
    }
}
