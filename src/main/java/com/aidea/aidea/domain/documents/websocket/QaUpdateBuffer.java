package com.aidea.aidea.domain.documents.websocket;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 질문 답변(전용 Y.Doc)의 Yjs 업데이트를 문서별로 메모리에만 보관한다.
 * 문서당 활성 Q&A는 최대 1개이므로 docId → 현재 qaId 하나의 업데이트 목록만 유지한다.
 * 서버는 Yjs 바이너리를 머지할 수 없어 쌓기만 하므로 총량 상한으로 방어한다.
 */
@Component
public class QaUpdateBuffer {

    static final long MAX_TOTAL_BYTES = 1024L * 1024L;

    private final ConcurrentHashMap<String, Entry> buffer = new ConcurrentHashMap<>();

    /**
     * qaId가 현재 버퍼와 다르면 새 qaId로 교체한 뒤 추가한다.
     * @return 총량 상한을 넘어 추가하지 못하면 false
     */
    public boolean append(String docId, String qaId, byte[] update) {
        boolean[] appended = {false};
        buffer.compute(docId, (key, entry) -> {
            Entry target = (entry == null || !entry.qaId().equals(qaId)) ? new Entry(qaId) : entry;
            appended[0] = target.add(update);
            return target;
        });
        return appended[0];
    }

    public Optional<String> currentQaId(String docId) {
        Entry entry = buffer.get(docId);
        return entry == null ? Optional.empty() : Optional.of(entry.qaId());
    }

    /** qaId가 현재 버퍼와 다르면 빈 리스트. 반환값은 복사본이다. */
    public List<byte[]> snapshot(String docId, String qaId) {
        Entry entry = buffer.get(docId);
        if (entry == null || !entry.qaId().equals(qaId)) {
            return Collections.emptyList();
        }
        return entry.copyUpdates();
    }

    public void clear(String docId) {
        buffer.remove(docId);
    }

    private static final class Entry {
        private final String qaId;
        private final List<byte[]> updates = new ArrayList<>();
        private long totalBytes;

        private Entry(String qaId) {
            this.qaId = qaId;
        }

        private String qaId() {
            return qaId;
        }

        private synchronized boolean add(byte[] update) {
            if (totalBytes + update.length > MAX_TOTAL_BYTES) {
                return false;
            }
            updates.add(update);
            totalBytes += update.length;
            return true;
        }

        private synchronized List<byte[]> copyUpdates() {
            return new ArrayList<>(updates);
        }
    }
}
