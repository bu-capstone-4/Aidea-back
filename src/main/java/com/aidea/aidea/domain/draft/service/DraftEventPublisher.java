package com.aidea.aidea.domain.draft.service;

/**
 * AI 초안 관련 이벤트를 클라이언트(WebSocket)에 푸시하는 추상 계약.
 * 구현은 DocumentWebSocketHandler가 담당한다.
 */
public interface DraftEventPublisher {

    void publishDraftToDocument(String documentId, String jsonEvent);
}
