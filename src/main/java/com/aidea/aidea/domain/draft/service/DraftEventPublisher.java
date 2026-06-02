package com.aidea.aidea.domain.draft.service;

/**
 * AI 초안 관련 이벤트를 클라이언트(WebSocket)에 푸시하는 추상 계약.
 * 구현은 DocumentWebSocketHandler가 담당한다.
 */
public interface DraftEventPublisher {

    /**
     * 특정 문서를 보고 있는 모든 WebSocket 세션에 JSON 이벤트를 푸시한다.
     *
     * @param documentId 대상 문서 ID
     * @param jsonEvent  완성된 JSON 문자열 (예: {"type":"draft:ready", ...})
     */
    void publishDraftToDocument(String documentId, String jsonEvent);
}
