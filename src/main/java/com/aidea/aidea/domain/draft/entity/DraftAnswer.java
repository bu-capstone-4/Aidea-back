package com.aidea.aidea.domain.draft.entity;

// 사용자가 제출하는 IDEA 구체화 질문에 대한 답변 한 개
public record DraftAnswer(
        String questionId, // DraftQuestion.id 참조
        String value       // 선택했거나 직접 입력한 답변
) {}
