package com.aidea.aidea.domain.documents.websocket;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DocumentUpdateBuffer {

    // docId → 수신 순서가 보장된 업데이트 바이너리 목록
    private final ConcurrentHashMap<String, List<byte[]>> buffer = new ConcurrentHashMap<>();

    public void push(String docId, byte[] updateBinary) {
        buffer.computeIfAbsent(docId, k -> Collections.synchronizedList(new ArrayList<>()))
              .add(updateBinary);
    }

    // 스케줄러 전용: 현재 버퍼를 반환하고 해당 docId 버퍼를 새 빈 리스트로 교체
    // ConcurrentHashMap.put은 원자적이므로, 교체 직후 들어오는 push는 새 리스트에 쌓임 → 유실 없음
    public List<byte[]> drainAndReset(String docId) {
        List<byte[]> drained = buffer.put(docId, Collections.synchronizedList(new ArrayList<>()));
        return drained != null ? new ArrayList<>(drained) : Collections.emptyList();
    }

    public boolean hasUpdates(String docId) {
        List<byte[]> list = buffer.get(docId);
        return list != null && !list.isEmpty();
    }

    public Set<String> getActiveDocIds() {
        return buffer.keySet();
    }
}
