package com.aidea.aidea.domain.documents.entity;

public enum DocumentType {
    IDEA,
    PLAN,
    USER_SCENARIO,
    API_SPEC,
    ERD;

    // AI 프롬프트에 주입할 문서 종류 한글 명칭
    public String displayName() {
        return switch (this) {
            case IDEA -> "서비스 아이디어 기획서";
            case PLAN -> "프로젝트 계획서";
            case USER_SCENARIO -> "유저 시나리오 문서";
            case API_SPEC -> "REST API 명세서";
            case ERD -> "ERD 설명 문서";
        };
    }

    // AI 프롬프트에 "이 문서가 반드시 다뤄야 할 핵심 항목"으로 주입할 목록
    public String requiredElements() {
        return switch (this) {
            case IDEA -> "핵심 가치(어떤 문제를 해결하는지), 타깃 사용자, 핵심 기능, 경쟁 서비스 대비 차별점";
            case PLAN -> "프로젝트 목표, 주요 기능 목록, 개발 단계(마일스톤)별 범위, 예상 일정";
            case USER_SCENARIO -> "주요 사용자 유형, Use Case별 진행 흐름(최소 3개), 예외/대안 흐름";
            case API_SPEC -> "주요 엔드포인트(HTTP 메서드+경로), 요청/응답 필드와 타입, 인증·에러 처리 방식";
            case ERD -> "주요 엔티티 목록, 엔티티별 핵심 필드와 타입, 엔티티 간 관계(1:N, N:M 등)";
        };
    }
}
