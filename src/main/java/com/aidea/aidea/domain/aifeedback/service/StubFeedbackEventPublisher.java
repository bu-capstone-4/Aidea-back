package com.aidea.aidea.domain.aifeedback.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 개발 중 임시 구현체.
 * 실제 WebSocket 발신 로직(Phase 2 담당자)이 등록되면 자동 비활성화.
 * ⚠ 운영 환경에서 이게 작동하면 안 됩니다.
 */
@Component
@ConditionalOnMissingBean(value = FeedbackEventPublisher.class, ignored = StubFeedbackEventPublisher.class)
@Slf4j
public class StubFeedbackEventPublisher implements FeedbackEventPublisher {

    @Override
    public void publishToDocument(String documentId, String jsonEvent) {
        log.warn("⚠ STUB FeedbackEventPublisher — 실제 WebSocket 발신 안 됨. " +
                        "docId={}, event={}",
                documentId, jsonEvent);
    }
}