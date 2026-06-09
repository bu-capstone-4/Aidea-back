package com.aidea.aidea.domain.draft.entity;

public enum DraftStatus {
    PENDING,      // Gemini 호출 중 (또는 IDEA 완료 대기 중)
    QUESTIONING,  // (IDEA 전용) 질문 생성 완료, 사용자 답변 대기
    ANSWERING,    // (IDEA 전용) 답변 받음, 최종 초안 생성 중
    DONE,         // 초안 생성 완료
    FAILED        // 실패
}
