package com.aidea.aidea.domain.documents.websocket;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 문서별 접속자 커서 awareness 상태를 in-memory로 관리한다.
 * awareness는 ephemeral 데이터이므로 DB에 저장하지 않는다.
 */
@Component
public class AwarenessStore {

    public record AwarenessEntry(long yjsClientId, String base64Update) {}

    // docId → sessionId → AwarenessEntry
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, AwarenessEntry>> store =
            new ConcurrentHashMap<>();

    public void put(String docId, String sessionId, long yjsClientId, String base64Update) {
        store.computeIfAbsent(docId, k -> new ConcurrentHashMap<>())
             .put(sessionId, new AwarenessEntry(yjsClientId, base64Update));
    }

    public Collection<AwarenessEntry> getAll(String docId) {
        ConcurrentHashMap<String, AwarenessEntry> docMap = store.get(docId);
        if (docMap == null) return Collections.emptyList();
        return docMap.values();
    }

    /** 세션 제거 후 해당 세션의 AwarenessEntry를 반환 (yjsClientId 추출용) */
    public AwarenessEntry remove(String docId, String sessionId) {
        ConcurrentHashMap<String, AwarenessEntry> docMap = store.get(docId);
        if (docMap == null) return null;
        AwarenessEntry removed = docMap.remove(sessionId);
        if (docMap.isEmpty()) store.remove(docId);
        return removed;
    }
}
