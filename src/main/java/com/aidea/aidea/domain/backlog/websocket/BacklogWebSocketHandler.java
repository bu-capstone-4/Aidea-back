package com.aidea.aidea.domain.backlog.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aidea.aidea.domain.backlog.dto.response.BacklogConfigResponse;
import com.aidea.aidea.domain.backlog.dto.response.BacklogTaskResponse;
import com.aidea.aidea.domain.backlog.dto.response.EpicResponse;
import com.aidea.aidea.domain.backlog.dto.response.StorySummaryResponse;
import com.aidea.aidea.domain.backlog.repository.BacklogConfigRepository;
import com.aidea.aidea.domain.backlog.repository.EpicRepository;
import com.aidea.aidea.domain.backlog.repository.StoryRepository;
import com.aidea.aidea.domain.backlog.repository.TaskRepository;
import com.aidea.aidea.domain.backlog.service.BacklogEventPublisher;
import com.aidea.aidea.global.websocket.SocketErrorCode;
import com.aidea.aidea.global.websocket.SocketErrorSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class BacklogWebSocketHandler extends TextWebSocketHandler implements BacklogEventPublisher {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> teamspaceSessions =
            new ConcurrentHashMap<>();

    private final EpicRepository epicRepository;
    private final StoryRepository storyRepository;
    private final TaskRepository taskRepository;
    private final BacklogConfigRepository backlogConfigRepository;
    private final SocketErrorSender socketErrorSender;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String teamspaceId = getAttr(session, "teamSpaceId");

        teamspaceSessions
                .computeIfAbsent(teamspaceId, k -> ConcurrentHashMap.newKeySet())
                .add(session);

        log.info("[WS-BACKLOG] connected sessionId={} teamspaceId={} userId={}",
                session.getId(), teamspaceId, getAttr(session, "userId"));

        sendInit(session, teamspaceId);
        broadcastPresence(teamspaceId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String teamspaceId = getAttr(session, "teamSpaceId");
        Set<WebSocketSession> sessions = teamspaceSessions.get(teamspaceId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) teamspaceSessions.remove(teamspaceId);
        }
        log.info("[WS-BACKLOG] disconnected sessionId={} teamspaceId={} status={}",
                session.getId(), teamspaceId, status);
        broadcastPresence(teamspaceId, null);
    }

    private void sendInit(WebSocketSession session, String teamspaceId) throws Exception {
        BacklogConfigResponse config = backlogConfigRepository.findById(teamspaceId)
                .map(BacklogConfigResponse::from)
                .orElse(BacklogConfigResponse.defaultFor(teamspaceId));

        Map<Long, int[]> epicStoryCounts = buildEpicStoryCounts(teamspaceId);
        List<EpicResponse> epics = epicRepository.findAllWithCreatorByTeamspaceId(teamspaceId)
                .stream().map(e -> {
                    int[] counts = epicStoryCounts.getOrDefault(e.getId(), new int[]{0, 0});
                    return EpicResponse.from(e, counts[0], counts[1]);
                }).toList();

        Map<Long, int[]> taskCounts = new HashMap<>();
        taskRepository.findTaskCountsByTeamspaceId(teamspaceId)
                .forEach(row -> taskCounts.put(
                        (Long) row[0],
                        new int[]{((Number) row[1]).intValue(), ((Number) row[2]).intValue()}
                ));

        List<StorySummaryResponse> stories = storyRepository.findAllWithRelationsByTeamspaceId(teamspaceId)
                .stream()
                .map(s -> {
                    int[] counts = taskCounts.getOrDefault(s.getId(), new int[]{0, 0});
                    return StorySummaryResponse.from(s, counts[0], counts[1]);
                })
                .toList();

        List<BacklogTaskResponse> standaloneTasks = taskRepository.findStandaloneByTeamspaceId(teamspaceId)
                .stream().map(BacklogTaskResponse::from).toList();

        List<Map<String, Object>> onlineEditors = buildOnlineEditors(teamspaceId);

        Map<String, Object> initPayload = new LinkedHashMap<>();
        initPayload.put("type", "backlog:init");
        initPayload.put("config", config);
        initPayload.put("epics", epics);
        initPayload.put("stories", stories);
        initPayload.put("tasks", standaloneTasks);
        initPayload.put("onlineEditors", onlineEditors);

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(initPayload)));
        log.debug("[WS-BACKLOG] init sent sessionId={} teamspaceId={}", session.getId(), teamspaceId);
    }

    private void broadcastPresence(String teamspaceId, String excludeSessionId) {
        Set<WebSocketSession> sessions = teamspaceSessions.get(teamspaceId);
        if (sessions == null || sessions.isEmpty()) return;

        List<Map<String, Object>> onlineEditors = buildOnlineEditors(teamspaceId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "backlog:presence");
        payload.put("onlineEditors", onlineEditors);

        try {
            String json = objectMapper.writeValueAsString(payload);
            TextMessage message = new TextMessage(json);
            sessions.forEach(s -> {
                if (s.isOpen()) {
                    try {
                        s.sendMessage(message);
                    } catch (IOException e) {
                        log.warn("[WS-BACKLOG] failed to send presence sessionId={}", s.getId());
                    }
                }
            });
        } catch (Exception e) {
            log.warn("[WS-BACKLOG] failed to serialize presence teamspaceId={}", teamspaceId);
        }
    }

    private List<Map<String, Object>> buildOnlineEditors(String teamspaceId) {
        Set<WebSocketSession> sessions = teamspaceSessions.get(teamspaceId);
        if (sessions == null) return List.of();

        List<Map<String, Object>> editors = new ArrayList<>();
        for (WebSocketSession s : sessions) {
            if (!s.isOpen()) continue;
            Map<String, Object> editor = new LinkedHashMap<>();
            editor.put("id", getAttr(s, "userId"));
            editor.put("name", getAttrOrEmpty(s, "userName"));
            editor.put("profileImageUrl", getAttrOrEmpty(s, "profileImageUrl"));
            editors.add(editor);
        }
        return editors;
    }

    private Map<Long, int[]> buildEpicStoryCounts(String teamspaceId) {
        Map<Long, int[]> counts = new HashMap<>();
        epicRepository.findStoryCountsByTeamspaceId(teamspaceId).forEach(row -> counts.put(
                (Long) row[0],
                new int[]{((Number) row[1]).intValue(), ((Number) row[2]).intValue()}
        ));
        return counts;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        socketErrorSender.send(session, SocketErrorCode.INVALID_MESSAGE);
    }

    @Override
    public void publishToTeamspace(String teamspaceId, String actorUserId, String jsonEvent) {
        Set<WebSocketSession> sessions = teamspaceSessions.get(teamspaceId);
        if (sessions == null) return;

        TextMessage message = new TextMessage(jsonEvent);
        sessions.forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    log.warn("[WS-BACKLOG] failed to send event teamspaceId={} sessionId={}", teamspaceId, session.getId());
                }
            }
        });
    }

    private String getAttr(WebSocketSession session, String key) {
        Object val = session.getAttributes().get(key);
        return val != null ? val.toString() : null;
    }

    private String getAttrOrEmpty(WebSocketSession session, String key) {
        Object val = session.getAttributes().get(key);
        return val != null ? val.toString() : "";
    }
}
