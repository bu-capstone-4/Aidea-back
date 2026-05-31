package com.aidea.aidea.domain.aifeedback.controller.dto;

import com.aidea.aidea.domain.aifeedback.entity.Answer;
import com.aidea.aidea.domain.aifeedback.entity.Feedback;
import com.aidea.aidea.domain.aifeedback.entity.FeedbackStatus;
import com.aidea.aidea.domain.aifeedback.entity.Question;

import java.util.List;

public record ActiveFeedbackInfo(
        String feedbackId,
        FeedbackStatus status,
        String revisedMarkdown,
        List<Question> questions
) {
    public static ActiveFeedbackInfo from(Feedback f) {
        return new ActiveFeedbackInfo(
                f.getId(),
                f.getStatus(),
                f.getRevisedMarkdown(),
                f.getQuestions()
        );
    }
}