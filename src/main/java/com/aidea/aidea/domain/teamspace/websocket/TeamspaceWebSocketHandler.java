package com.aidea.aidea.domain.teamspace.websocket;

import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.domain.teamspace.entity.TeamSpace;
import com.aidea.aidea.domain.teamspace.repository.TeamSpaceRepository;
import com.aidea.aidea.domain.teamspace.service.TeamspaceEventPublisher;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TeamspaceWebSocketHandler extends TextWebSocketHandler
        implements TeamspaceEventPublisher {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> teamspaceSessions
            = new ConcurrentHashMap<>();

    private final TeamSpaceRepository teamSpaceRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final SocketErrorSender socketErrorSender;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String teamspaceId = (String) session.getAttributes().get("teamspaceId");
        String userId = (String) session.getAttributes().get("userId");
        MemberRole role = (MemberRole) session.getAttributes().get("role");

        teamspaceSessions.computeIfAbsent(teamspaceId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("[WS-TS] connected sessionId={} teamspaceId={} userId={} role={}", session.getId(), teamspaceId, userId, role);

        sendTeamspaceInit(session, teamspaceId);
        broadcastMemberUpdate(session, teamspaceId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(message.getPayload(), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("[WS-TS] invalid JSON sessionId={}", session.getId());
            socketErrorSender.send(session, SocketErrorCode.INVALID_MESSAGE);
            return;
        }

        String event = (String) payload.get("event");
        if (event == null) {
            socketErrorSender.send(session, SocketErrorCode.INVALID_MESSAGE);
            return;
        }

        try {
            switch (event) {
                case "member:focus" -> handleMemberFocus(session, payload);
                default -> {
                    log.warn("[WS-TS] unknown event={} sessionId={}", event, session.getId());
                    socketErrorSender.send(session, SocketErrorCode.INVALID_MESSAGE);
                }
            }
        } catch (Exception e) {
            log.error("[WS-TS] unhandled error event={} sessionId={}", event, session.getId(), e);
            socketErrorSender.send(session, SocketErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[WS-TS] transport error sessionId={}", session.getId(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String teamspaceId = (String) session.getAttributes().get("teamspaceId");
        String userId = (String) session.getAttributes().get("userId");

        Set<WebSocketSession> sessions = teamspaceSessions.get(teamspaceId);
        if (sessions != null) {
            sessions.remove(session);
        }
        log.info("[WS-TS] disconnected sessionId={} teamspaceId={} userId={} status={}", session.getId(), teamspaceId, userId, status.getCode());

        broadcastMemberUpdate(null, teamspaceId);
    }

    private void handleMemberFocus(WebSocketSession session, Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        String documentId = data != null ? (String) data.get("documentId") : null;

        session.getAttributes().put("currentDocumentId", documentId);

        String teamspaceId = (String) session.getAttributes().get("teamspaceId");
        String userId = (String) session.getAttributes().get("userId");
        log.debug("[WS-TS] member:focus userId={} teamspaceId={} documentId={}", userId, teamspaceId, documentId);

        broadcastMemberUpdate(null, teamspaceId);
    }

    private void sendTeamspaceInit(WebSocketSession session, String teamspaceId) throws Exception {
        TeamSpace teamSpace = teamSpaceRepository.findById(teamspaceId).orElse(null);
        if (teamSpace == null) {
            log.warn("[WS-TS] sendTeamspaceInit skipped - teamspace not found teamspaceId={} sessionId={}", teamspaceId, session.getId());
            return;
        }

        Map<String, Object> teamspaceData = new LinkedHashMap<>();
        teamspaceData.put("id", teamSpace.getTeamspaceId());
        teamspaceData.put("name", teamSpace.getName());

        List<Map<String, Object>> onlineMembers = buildOnlineMembers(teamspaceId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("teamspace", teamspaceData);
        data.put("onlineMembers", onlineMembers);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "teamspace:init");
        event.put("data", data);

        log.warn("[WS-TS] sending teamspace:init sessionId={} teamspaceId={} onlineMemberCount={}", session.getId(), teamspaceId, onlineMembers.size());
        sendSafe(session, new TextMessage(objectMapper.writeValueAsString(event)));
    }

    private void broadcastMemberUpdate(WebSocketSession exclude, String teamspaceId) {
        Set<WebSocketSession> sessions = teamspaceSessions.getOrDefault(teamspaceId, Collections.emptySet());
        if (sessions.isEmpty()) return;

        try {
            List<Map<String, Object>> onlineMembers = buildOnlineMembers(teamspaceId);

            Map<String, Object> data = Map.of("onlineMembers", onlineMembers);
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event", "member:update");
            event.put("data", data);

            TextMessage message = new TextMessage(objectMapper.writeValueAsString(event));

            for (WebSocketSession s : sessions) {
                if (!s.isOpen()) continue;
                if (exclude != null && s.getId().equals(exclude.getId())) continue;
                sendSafe(s, message);
            }
        } catch (JsonProcessingException e) {
            log.error("[WS-TS] failed to serialize member:update teamspaceId={}", teamspaceId, e);
        }
    }

    @Override
    public void publishDraftReady(String teamspaceId, String documentId, String draftId, String content) {
        log.warn("[WS-TS] publishDraftReady teamspaceId={} documentId={} draftId={}", teamspaceId, documentId, draftId);
        Map<String, Object> data = Map.of(
                "documentId", documentId,
                "draftId", draftId,
                "content", content
        );
        publishEvent(teamspaceId, "draft:ready", data);
    }

    @Override
    public void publishDraftError(String teamspaceId, String documentId, String errorCode) {
        log.warn("[WS-TS] publishDraftError teamspaceId={} documentId={} errorCode={}", teamspaceId, documentId, errorCode);
        publishEvent(teamspaceId, "draft:error", Map.of("documentId", documentId, "errorCode", errorCode));
    }

    @Override
    public void publishDraftQuestioning(String teamspaceId, String documentId, String draftId,
                                         List<com.aidea.aidea.domain.draft.entity.DraftQuestion> questions) {
        log.warn("[WS-TS] publishDraftQuestioning teamspaceId={} documentId={} draftId={} questionCount={}",
                teamspaceId, documentId, draftId, questions.size());
        Map<String, Object> data = Map.of(
                "documentId", documentId,
                "draftId", draftId,
                "questions", questions
        );
        publishEvent(teamspaceId, "draft:questioning", data);
    }

    private void publishEvent(String teamspaceId, String eventType, Map<String, Object> data) {
        Set<WebSocketSession> sessions = teamspaceSessions.getOrDefault(teamspaceId, Collections.emptySet());
        if (sessions.isEmpty()) {
            log.warn("[WS-TS] publishEvent no active sessions event={} teamspaceId={}", eventType, teamspaceId);
            return;
        }
        log.warn("[WS-TS] publishEvent event={} teamspaceId={} sessionCount={}", eventType, teamspaceId, sessions.size());

        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event", eventType);
            event.put("data", data);
            TextMessage message = new TextMessage(objectMapper.writeValueAsString(event));
            for (WebSocketSession s : sessions) {
                sendSafe(s, message);
            }
        } catch (JsonProcessingException e) {
            log.error("[WS-TS] failed to serialize {} teamspaceId={}", eventType, teamspaceId, e);
        }
    }

    private void sendSafe(WebSocketSession session, TextMessage message) {
        synchronized (session) {
            if (!session.isOpen()) return;
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                log.warn("[WS-TS] send failed sessionId={}", session.getId());
            }
        }
    }

    private List<Map<String, Object>> buildOnlineMembers(String teamspaceId) {
        Set<WebSocketSession> sessions = teamspaceSessions.getOrDefault(teamspaceId, Collections.emptySet());

        List<Long> userIds = sessions.stream()
                .filter(WebSocketSession::isOpen)
                .map(s -> Long.parseLong((String) s.getAttributes().get("userId")))
                .distinct()
                .collect(Collectors.toList());

        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Map<Long, WebSocketSession> firstSessionByUser = new LinkedHashMap<>();
        for (WebSocketSession s : sessions) {
            if (!s.isOpen()) continue;
            Long uid = Long.parseLong((String) s.getAttributes().get("userId"));
            firstSessionByUser.putIfAbsent(uid, s);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, WebSocketSession> entry : firstSessionByUser.entrySet()) {
            Long uid = entry.getKey();
            WebSocketSession s = entry.getValue();
            User user = userMap.get(uid);
            if (user == null) continue;

            Map<String, Object> member = new LinkedHashMap<>();
            member.put("userId", uid);
            member.put("name", user.getName());
            member.put("profileImageUrl", user.getProfileImageUrl());
            member.put("role", s.getAttributes().get("role"));
            member.put("currentDocumentId", s.getAttributes().get("currentDocumentId"));
            result.add(member);
        }
        return result;
    }
}
