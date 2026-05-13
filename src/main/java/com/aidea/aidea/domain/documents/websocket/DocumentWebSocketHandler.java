package com.aidea.aidea.domain.documents.websocket;

import com.aidea.aidea.domain.documents.service.DocumentService;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentWebSocketHandler extends TextWebSocketHandler {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> docSessions
            = new ConcurrentHashMap<>();

    private final DocumentUpdateBuffer updateBuffer;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    // --- 연결 수립 ---

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String docId = (String) session.getAttributes().get("docId");
        String userId = (String) session.getAttributes().get("userId");
        MemberRole role = (MemberRole) session.getAttributes().get("role");

        docSessions.computeIfAbsent(docId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("[WS] connected sessionId={} docId={} userId={} role={}", session.getId(), docId, userId, role);

        sendDocInit(session, docId);
    }

    // --- 메시지 수신 ---

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(message.getPayload(), new TypeReference<>() {});
        String type = (String) payload.get("type");

        switch (type) {
            case "doc:update" -> handleDocUpdate(session, payload);
        }
    }

    // --- 연결 종료 ---

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String docId = (String) session.getAttributes().get("docId");
        String userId = (String) session.getAttributes().get("userId");

        Set<WebSocketSession> sessions = docSessions.get(docId);
        if (sessions != null) {
            sessions.remove(session);
        }
        log.info("[WS] disconnected sessionId={} docId={} userId={} status={}", session.getId(), docId, userId, status.getCode());
    }

    // --- 내부 처리 메서드 ---

    private void sendDocInit(WebSocketSession session, String docId) throws Exception {
        byte[] snapshot = documentService.getSnapshot(docId);
        List<byte[]> dbUpdates = documentService.getPendingUpdates(docId);

        List<String> updates = new ArrayList<>();
        if (snapshot != null) {
            updates.add(Base64.getEncoder().encodeToString(snapshot));
        }
        for (byte[] u : dbUpdates) {
            updates.add(Base64.getEncoder().encodeToString(u));
        }

        log.debug("[WS] sendDocInit sessionId={} docId={} snapshotPresent={} pendingUpdates={}",
                session.getId(), docId, snapshot != null, dbUpdates.size());

        Map<String, Object> event = Map.of("type", "doc:init", "updates", updates);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
    }

    private void handleDocUpdate(WebSocketSession session, Map<String, Object> payload) throws Exception {
        MemberRole role = (MemberRole) session.getAttributes().get("role");

        if (role == MemberRole.VIEWER) {
            String userId = (String) session.getAttributes().get("userId");
            String docId = (String) session.getAttributes().get("docId");
            log.warn("[WS] doc:update rejected userId={} docId={} reason=VIEWER_NOT_ALLOWED", userId, docId);
            return;
        }

        String docId = (String) session.getAttributes().get("docId");
        String clientId = (String) session.getAttributes().get("userId");
        String base64Update = (String) payload.get("update");
        byte[] updateBinary = Base64.getDecoder().decode(base64Update);

        log.debug("[WS] doc:update docId={} clientId={} bytes={}", docId, clientId, updateBinary.length);

        updateBuffer.push(docId, updateBinary);
        documentService.saveUpdate(docId, updateBinary, clientId);

        String broadcastJson = objectMapper.writeValueAsString(
                Map.of("type", "doc:update", "update", base64Update)
        );
        broadcastToOthers(session, docId, new TextMessage(broadcastJson));
    }

    private void broadcastToOthers(WebSocketSession sender, String docId, TextMessage message) {
        Set<WebSocketSession> sessions = docSessions.getOrDefault(docId, Collections.emptySet());
        for (WebSocketSession s : sessions) {
            if (s.isOpen() && !s.getId().equals(sender.getId())) {
                try {
                    s.sendMessage(message);
                } catch (IOException e) {
                    log.warn("[WS] broadcast failed sessionId={} docId={}", s.getId(), docId);
                }
            }
        }
    }

    // Phase 4의 GeminiService가 피드백 이벤트 푸시 시 사용
    public void pushToDocument(String docId, String jsonEvent) {
        TextMessage message = new TextMessage(jsonEvent);
        Set<WebSocketSession> sessions = docSessions.getOrDefault(docId, Collections.emptySet());
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(message);
                } catch (IOException e) {
                    log.warn("[WS] pushToDocument failed sessionId={} docId={}", s.getId(), docId);
                }
            }
        }
    }
}
