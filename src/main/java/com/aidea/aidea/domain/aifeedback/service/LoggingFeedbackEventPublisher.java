package com.aidea.aidea.domain.aifeedback.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 개발 중 임시 구현체.
 * 실제 DocumentWebSocketHandler가 FeedbackEventPublisher를 구현하면
 * 자동으로 이 빈은 비활성화된다 (@ConditionalOnMissingBean).
 */
@Component
@ConditionalOnMissingBean(name = "documentWebSocketHandler")
@Slf4j
public class LoggingFeedbackEventPublisher implements FeedbackEventPublisher {

    @Override
    public void publishToDocument(String documentId, String jsonEvent) {
        log.info("[STUB] Would publish to docId={} event={}", documentId, jsonEvent);
    }
}
