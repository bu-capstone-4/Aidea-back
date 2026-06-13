package com.aidea.aidea.domain.documents.websocket;

import com.aidea.aidea.domain.aifeedback.repository.FeedbackRepository;
import com.aidea.aidea.domain.documents.service.DocumentService;
import com.aidea.aidea.domain.draft.repository.DraftRepository;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.global.websocket.SocketErrorSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentWebSocketHandlerTest {

    @Mock
    private DocumentUpdateBuffer updateBuffer;
    @Mock
    private DocumentService documentService;
    @Mock
    private FeedbackRepository feedbackRepository;
    @Mock
    private DraftRepository draftRepository;
    @Mock
    private SocketErrorSender socketErrorSender;
    @Mock
    private AwarenessStore awarenessStore;

    private DocumentWebSocketHandler handler;

    private static final String TEAMSPACE_ID = "ts-1";
    private static final String OTHER_TEAMSPACE_ID = "ts-2";

    @BeforeEach
    void setUp() {
        handler = new DocumentWebSocketHandler(updateBuffer, documentService, feedbackRepository, draftRepository,
                new ObjectMapper(), socketErrorSender, awarenessStore);
    }

    private WebSocketSession sessionWith(String docId, String teamspaceId, String userId, MemberRole role) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("docId", docId);
        attributes.put("teamspaceId", teamspaceId);
        attributes.put("userId", userId);
        attributes.put("role", role);
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }

    @SuppressWarnings("unchecked")
    private void registerSession(String docId, WebSocketSession session) throws Exception {
        Field field = DocumentWebSocketHandler.class.getDeclaredField("docSessions");
        field.setAccessible(true);
        ConcurrentHashMap<String, Set<WebSocketSession>> sessions =
                (ConcurrentHashMap<String, Set<WebSocketSession>>) field.get(handler);
        sessions.computeIfAbsent(docId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Test
    void onMemberRoleChanged_updatesCachedRole_forSameTeamspaceAndUser() throws Exception {
        WebSocketSession targetSession = sessionWith("doc-1", TEAMSPACE_ID, "2", MemberRole.VIEWER);
        registerSession("doc-1", targetSession);

        handler.onMemberRoleChanged(TEAMSPACE_ID, 2L, MemberRole.MEMBER);

        assertThat(targetSession.getAttributes().get("role")).isEqualTo(MemberRole.MEMBER);
    }

    @Test
    void onMemberRoleChanged_doesNotUpdateOtherUsersSessions() throws Exception {
        WebSocketSession targetSession = sessionWith("doc-1", TEAMSPACE_ID, "2", MemberRole.VIEWER);
        WebSocketSession otherUserSession = sessionWith("doc-1", TEAMSPACE_ID, "3", MemberRole.MEMBER);
        registerSession("doc-1", targetSession);
        registerSession("doc-1", otherUserSession);

        handler.onMemberRoleChanged(TEAMSPACE_ID, 2L, MemberRole.MEMBER);

        assertThat(targetSession.getAttributes().get("role")).isEqualTo(MemberRole.MEMBER);
        assertThat(otherUserSession.getAttributes().get("role")).isEqualTo(MemberRole.MEMBER); // unaffected
    }

    @Test
    void onMemberRoleChanged_doesNotUpdateSessionsFromOtherTeamspaces() throws Exception {
        WebSocketSession sameTeamspaceSession = sessionWith("doc-1", TEAMSPACE_ID, "2", MemberRole.VIEWER);
        WebSocketSession otherTeamspaceSession = sessionWith("doc-2", OTHER_TEAMSPACE_ID, "2", MemberRole.VIEWER);
        registerSession("doc-1", sameTeamspaceSession);
        registerSession("doc-2", otherTeamspaceSession);

        handler.onMemberRoleChanged(TEAMSPACE_ID, 2L, MemberRole.MEMBER);

        assertThat(sameTeamspaceSession.getAttributes().get("role")).isEqualTo(MemberRole.MEMBER);
        assertThat(otherTeamspaceSession.getAttributes().get("role")).isEqualTo(MemberRole.VIEWER);
    }

    @Test
    void onMemberRoleChanged_updatesAllDocSessions_forUserConnectedToMultipleDocuments() throws Exception {
        WebSocketSession docASession = sessionWith("doc-A", TEAMSPACE_ID, "2", MemberRole.VIEWER);
        WebSocketSession docBSession = sessionWith("doc-B", TEAMSPACE_ID, "2", MemberRole.VIEWER);
        registerSession("doc-A", docASession);
        registerSession("doc-B", docBSession);

        handler.onMemberRoleChanged(TEAMSPACE_ID, 2L, MemberRole.MEMBER);

        assertThat(docASession.getAttributes().get("role")).isEqualTo(MemberRole.MEMBER);
        assertThat(docBSession.getAttributes().get("role")).isEqualTo(MemberRole.MEMBER);
    }
}
