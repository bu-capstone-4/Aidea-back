package com.aidea.aidea.domain.documents.websocket;

import com.aidea.aidea.domain.aifeedback.entity.FeedbackStatus;
import com.aidea.aidea.domain.aifeedback.repository.FeedbackRepository;
import com.aidea.aidea.domain.aifeedback.service.FeedbackEventPublisher;
import com.aidea.aidea.domain.draft.service.DraftEventPublisher;
import com.aidea.aidea.domain.documents.dto.ActiveDraftInfo;
import com.aidea.aidea.domain.documents.dto.ActiveFeedbackInfo;
import com.aidea.aidea.domain.documents.service.DocumentService;
import com.aidea.aidea.domain.draft.repository.DraftRepository;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.global.websocket.SocketErrorCode;
import com.aidea.aidea.global.websocket.SocketErrorSender;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentWebSocketHandler extends TextWebSocketHandler implements FeedbackEventPublisher, DraftEventPublisher {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> docSessions
            = new ConcurrentHashMap<>();

    private static final List<FeedbackStatus> TERMINAL_STATUSES =
            List.of(FeedbackStatus.ACCEPTED, FeedbackStatus.REJECTED, FeedbackStatus.FAILED);

    private final DocumentUpdateBuffer updateBuffer;
    private final DocumentService documentService;
    private final FeedbackRepository feedbackRepository;
    private final DraftRepository draftRepository;
    private final ObjectMapper objectMapper;
    private final SocketErrorSender socketErrorSender;

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
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(message.getPayload(), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("[WS] invalid JSON sessionId={}", session.getId());
            socketErrorSender.send(session, SocketErrorCode.INVALID_MESSAGE);
            return;
        }

        String type = (String) payload.get("type");
        if (type == null) {
            socketErrorSender.send(session, SocketErrorCode.INVALID_MESSAGE);
            return;
        }

        try {
            switch (type) {
                case "doc:update" -> handleDocUpdate(session, payload);
                default -> {
                    log.warn("[WS] unknown message type={} sessionId={}", type, session.getId());
                    socketErrorSender.send(session, SocketErrorCode.INVALID_MESSAGE);
                }
            }
        } catch (Exception e) {
            log.error("[WS] unhandled error type={} sessionId={}", type, session.getId(), e);
            socketErrorSender.send(session, SocketErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[WS] transport error sessionId={}", session.getId(), exception);
        socketErrorSender.send(session, SocketErrorCode.INTERNAL_SERVER_ERROR);
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

        ActiveFeedbackInfo activeFeedback = feedbackRepository
                .findTopByDocumentIdAndStatusNotInOrderByCreatedAtDesc(docId, TERMINAL_STATUSES)
                .map(fb -> new ActiveFeedbackInfo(
                        fb.getId(),
                        fb.getStatus(),
                        fb.getRevisedMarkdown(),
                        fb.getStatus() == FeedbackStatus.QUESTIONING ? fb.getQuestions() : null
                ))
                .orElse(null);

        log.debug("[WS] sendDocInit sessionId={} docId={} snapshotPresent={} pendingUpdates={} activeFeedbackStatus={}",
                session.getId(), docId, snapshot != null, dbUpdates.size(),
                activeFeedback != null ? activeFeedback.status() : "none");

        ActiveDraftInfo activeDraft = draftRepository.findByDocumentId(docId)
                .map(d -> new ActiveDraftInfo(d.getId(), d.getStatus(), d.getContent()))
                .orElse(null);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "doc:init");
        event.put("updates", updates);
        event.put("activeFeedback", activeFeedback);
        event.put("activeDraft", activeDraft);
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

        if (base64Update == null || base64Update.isBlank()) {
            log.warn("[WS] doc:update missing update field sessionId={}", session.getId());
            socketErrorSender.send(session, SocketErrorCode.INVALID_MESSAGE);
            return;
        }

        byte[] updateBinary;
        try {
            updateBinary = Base64.getDecoder().decode(base64Update);
        } catch (IllegalArgumentException e) {
            log.warn("[WS] doc:update invalid base64 sessionId={}", session.getId());
            socketErrorSender.send(session, SocketErrorCode.INVALID_MESSAGE);
            return;
        }

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

    @Override
    public void publishToDocument(String documentId, String jsonEvent) {
        TextMessage message = new TextMessage(jsonEvent);
        Set<WebSocketSession> sessions = docSessions.getOrDefault(documentId, Collections.emptySet());
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(message);
                } catch (IOException e) {
                    log.warn("[WS] publishToDocument failed sessionId={} docId={}", s.getId(), documentId);
                }
            }
        }
    }

    @Override
    public void publishDraftToDocument(String documentId, String jsonEvent) {
        TextMessage message = new TextMessage(jsonEvent);
        Set<WebSocketSession> sessions = docSessions.getOrDefault(documentId, Collections.emptySet());
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(message);
                } catch (IOException e) {
                    log.warn("[WS] publishDraftToDocument failed sessionId={} docId={}", s.getId(), documentId);
                }
            }
        }
    }

}
