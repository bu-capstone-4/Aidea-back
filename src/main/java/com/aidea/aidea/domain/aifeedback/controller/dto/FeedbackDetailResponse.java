package com.aidea.aidea.domain.aifeedback.controller.dto;

import com.aidea.aidea.domain.aifeedback.entity.Answer;
import com.aidea.aidea.domain.aifeedback.entity.Feedback;
import com.aidea.aidea.domain.aifeedback.entity.FeedbackStatus;
import com.aidea.aidea.domain.aifeedback.entity.Question;

import java.time.LocalDateTime;
import java.util.List;

public record FeedbackDetailResponse(
        String feedbackId,
        String documentId,
        FeedbackStatus status,
        String originalMarkdown,
        String revisedMarkdown,
        List<Question> questions,
        List<Answer> answers,
        String additionalRequest,
        LocalDateTime createdAt
) {

    public static FeedbackDetailResponse from(Feedback f) {
        return new FeedbackDetailResponse(
                f.getId(),
                f.getDocument().getId(),
                f.getStatus(),
                f.getOriginalMarkdown(),
                f.getRevisedMarkdown(),
                f.getQuestions(),
                f.getAnswers(),
                f.getAdditionalRequest(),
                f.getCreatedAt()
        );
    }
}

