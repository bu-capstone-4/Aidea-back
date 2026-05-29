package com.aidea.aidea.domain.draft.entity;

public enum DraftStatus {
    PENDING,  // Gemini 호출 중
    DONE,     // 초안 생성 완료
    FAILED    // 실패
}
