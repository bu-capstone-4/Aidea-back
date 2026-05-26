package com.aidea.aidea.domain.backlog.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aidea.aidea.domain.backlog.dto.response.BacklogConfigResponse;
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
    }

    private void sendInit(WebSocketSession session, String teamspaceId) throws Exception {
        BacklogConfigResponse config = backlogConfigRepository.findById(teamspaceId)
                .map(BacklogConfigResponse::from)
                .orElse(BacklogConfigResponse.defaultFor(teamspaceId));
        List<EpicResponse> epics = epicRepository.findAllWithCreatorByTeamspaceId(teamspaceId)
                .stream().map(EpicResponse::from).toList();

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

        Map<String, Object> initPayload = new LinkedHashMap<>();
        initPayload.put("type", "backlog:init");
        initPayload.put("config", config);
        initPayload.put("epics", epics);
        initPayload.put("stories", stories);

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(initPayload)));
        log.debug("[WS-BACKLOG] init sent sessionId={} teamspaceId={}", session.getId(), teamspaceId);
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
            if (actorUserId.equals(getAttr(session, "userId"))) return;

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
        return (String) session.getAttributes().get(key);
    }
}
