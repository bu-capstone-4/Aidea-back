package com.aidea.aidea.domain.documents.dto;

import com.aidea.aidea.domain.aifeedback.entity.FeedbackStatus;
import com.aidea.aidea.domain.aifeedback.entity.Question;

import java.util.List;

public record ActiveFeedbackInfo(
        String feedbackId,
        FeedbackStatus status,
        String revisedMarkdown,
        List<Question> questions
) {}
