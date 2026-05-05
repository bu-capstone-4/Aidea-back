package com.aidea.aidea.domain.aifeedback.entity;

public enum FeedbackStatus {
    PENDING,       // Gemini 호출 중 (첫 요청 직후)
    QUESTIONING,   // 질문 생성 완료, 사용자 답변 대기
    ANSWERING,     // 사용자 답변 받음, Gemini 재호출 중
    DONE,          // 수정안 생성 완료, 사용자 검토 대기
    ACCEPTED,      // 사용자가 수정안 수락 → 프론트가 BlockNote에 적용
    REJECTED       // 사용자가 원본 유지
}
