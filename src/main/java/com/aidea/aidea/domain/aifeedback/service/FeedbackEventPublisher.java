package com.aidea.aidea.domain.aifeedback.service;
/**
 * AI 피드백 관련 이벤트를 클라이언트(WebSocket)에 푸시하는 추상 계약.
 * 실제 구현은 Phase 2의 DocumentWebSocketHandler가 담당하지만,
 * 본 인터페이스만 의존하여 테스트와 개발이 독립적으로 가능.
 */
public interface FeedbackEventPublisher {

    /**
     * 특정 문서를 보고 있는 모든 WebSocket 세션에 JSON 이벤트를 푸시한다.
     *
     * @param documentId 대상 문서 ID
     * @param jsonEvent  완성된 JSON 문자열 (예: {"type":"feedback:ready", ...})
     */
    void publishToDocument(String documentId, String jsonEvent);
}
