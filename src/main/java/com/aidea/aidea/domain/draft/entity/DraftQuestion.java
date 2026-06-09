package com.aidea.aidea.domain.draft.entity;

import java.util.List;

// IDEA 초안을 구체화하기 위해 Gemini가 생성하는 질문 한 개 (항상 객관식 보기를 포함)
public record DraftQuestion(
        String id,
        String section,
        String text,
        List<String> options
) {}
