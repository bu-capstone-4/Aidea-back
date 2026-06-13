package com.aidea.aidea.domain.teamspace.websocket;

import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.domain.teamspace.repository.TeamSpaceRepository;
import com.aidea.aidea.global.websocket.SocketErrorSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamspaceWebSocketHandlerTest {

    @Mock
    private TeamSpaceRepository teamSpaceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SocketErrorSender socketErrorSender;

    private TeamspaceWebSocketHandler handler;

    private static final String TEAMSPACE_ID = "ts-1";

    @BeforeEach
    void setUp() {
        handler = new TeamspaceWebSocketHandler(teamSpaceRepository, userRepository, new ObjectMapper(), socketErrorSender);
    }

    private WebSocketSession sessionWith(String userId, MemberRole role) {
        WebSocketSession session = org.mockito.Mockito.mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("teamspaceId", TEAMSPACE_ID);
        attributes.put("userId", userId);
        attributes.put("role", role);
        when(session.getAttributes()).thenReturn(attributes);
        lenient().when(session.getId()).thenReturn("session-" + userId);
        lenient().when(session.isOpen()).thenReturn(true);
        return session;
    }

    @SuppressWarnings("unchecked")
    private void registerSession(WebSocketSession session) throws Exception {
        Field field = TeamspaceWebSocketHandler.class.getDeclaredField("teamspaceSessions");
        field.setAccessible(true);
        ConcurrentHashMap<String, Set<WebSocketSession>> sessions =
                (ConcurrentHashMap<String, Set<WebSocketSession>>) field.get(handler);
        sessions.computeIfAbsent(TEAMSPACE_ID, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Test
    void onMemberRoleChanged_updatesCachedRoleForTargetUserSessions() throws Exception {
        WebSocketSession targetSession = sessionWith("2", MemberRole.VIEWER);
        WebSocketSession otherSession = sessionWith("3", MemberRole.MEMBER);
        registerSession(targetSession);
        registerSession(otherSession);

        handler.onMemberRoleChanged(TEAMSPACE_ID, 2L, MemberRole.MEMBER);

        assertThat(targetSession.getAttributes().get("role")).isEqualTo(MemberRole.MEMBER);
        assertThat(otherSession.getAttributes().get("role")).isEqualTo(MemberRole.MEMBER); // unaffected, already MEMBER
    }

    @Test
    void onMemberRoleChanged_broadcastsRoleChangedEventToAllSessions() throws Exception {
        WebSocketSession targetSession = sessionWith("2", MemberRole.VIEWER);
        WebSocketSession otherSession = sessionWith("3", MemberRole.MEMBER);
        registerSession(targetSession);
        registerSession(otherSession);

        handler.onMemberRoleChanged(TEAMSPACE_ID, 2L, MemberRole.MEMBER);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(targetSession).sendMessage(captor.capture());
        verify(otherSession).sendMessage(captor.capture());

        for (TextMessage message : captor.getAllValues()) {
            String payload = message.getPayload();
            assertThat(payload).contains("\"event\":\"member:role_changed\"");
            assertThat(payload).contains("\"userId\":2");
            assertThat(payload).contains("\"role\":\"MEMBER\"");
        }
    }

    @Test
    void onMemberRoleChanged_doesNotUpdateOtherUsersCachedRole() throws Exception {
        WebSocketSession targetSession = sessionWith("2", MemberRole.VIEWER);
        WebSocketSession otherSession = sessionWith("3", MemberRole.VIEWER);
        registerSession(targetSession);
        registerSession(otherSession);

        handler.onMemberRoleChanged(TEAMSPACE_ID, 2L, MemberRole.MEMBER);

        assertThat(targetSession.getAttributes().get("role")).isEqualTo(MemberRole.MEMBER);
        assertThat(otherSession.getAttributes().get("role")).isEqualTo(MemberRole.VIEWER);
    }
}
