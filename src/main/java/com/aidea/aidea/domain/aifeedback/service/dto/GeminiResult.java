package com.aidea.aidea.domain.aifeedback.service.dto;

import com.aidea.aidea.domain.aifeedback.entity.Question;

import java.util.List;

//Gemini가 우리 JSON 스키마대로 돌려준 결과를 담는 DTO
public record GeminiResult(
        Type type,
        String revisedMarkdown,  //type=FEEDBACK일 때만 채워짐
        List<Question> questions //type=QUESTIONS일 때만 채워짐
) {
    public enum Type {
        FEEDBACK,
        QUESTIONS
    }
}