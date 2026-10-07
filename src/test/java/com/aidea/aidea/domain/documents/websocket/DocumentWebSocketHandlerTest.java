package com.aidea.aidea.domain.documents.websocket;

import com.aidea.aidea.domain.aifeedback.entity.Feedback;
import com.aidea.aidea.domain.aifeedback.entity.FeedbackStatus;
import com.aidea.aidea.domain.aifeedback.repository.FeedbackRepository;
import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.service.DocumentService;
import com.aidea.aidea.domain.draft.entity.Draft;
import com.aidea.aidea.domain.draft.entity.DraftStatus;
import com.aidea.aidea.domain.draft.repository.DraftRepository;
import com.aidea.aidea.domain.teamspace.entity.MemberRole;
import com.aidea.aidea.global.websocket.SocketErrorCode;
import com.aidea.aidea.global.websocket.SocketErrorSender;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    private QaUpdateBuffer qaUpdateBuffer;
    private DocumentWebSocketHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TEAMSPACE_ID = "ts-1";
    private static final String OTHER_TEAMSPACE_ID = "ts-2";
    private static final String DOC_ID = "doc-1";
    private static final String QA_ID = "qa-1";
    private static final String UPDATE_B64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});

    @BeforeEach
    void setUp() {
        qaUpdateBuffer = new QaUpdateBuffer();
        handler = new DocumentWebSocketHandler(updateBuffer, documentService, feedbackRepository, draftRepository,
                objectMapper, socketErrorSender, awarenessStore, qaUpdateBuffer);
    }

    private WebSocketSession openSession(String sessionId, MemberRole role) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("docId", DOC_ID);
        attributes.put("teamspaceId", TEAMSPACE_ID);
        attributes.put("userId", sessionId);
        attributes.put("role", role);
        lenient().when(session.getAttributes()).thenReturn(attributes);
        lenient().when(session.getId()).thenReturn(sessionId);
        lenient().when(session.isOpen()).thenReturn(true);
        return session;
    }

    private TextMessage qaUpdateMessage(String qaId, String update) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "qa:update");
        payload.put("qaId", qaId);
        payload.put("update", update);
        return new TextMessage(objectMapper.writeValueAsString(payload));
    }

    private Feedback feedbackWith(String id, String docId, FeedbackStatus status) {
        Document document = mock(Document.class);
        lenient().when(document.getId()).thenReturn(docId);
        Feedback feedback = Feedback.create(id, document, null, "", null, null);
        feedback.setStatus(status);
        return feedback;
    }

    private Draft draftWith(String id, DraftStatus status) {
        Draft draft = Draft.create(id, mock(Document.class), "");
        draft.setStatus(status);
        return draft;
    }

    private JsonNode lastSentJson(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        return objectMapper.readTree(captor.getValue().getPayload());
    }

    // ───── qa:update ─────

    @Test
    void qaUpdate_fromViewer_isNeitherBufferedNorBroadcast() throws Exception {
        WebSocketSession viewer = openSession("s-viewer", MemberRole.VIEWER);
        WebSocketSession other = openSession("s-other", MemberRole.MEMBER);
        registerSession(DOC_ID, viewer);
        registerSession(DOC_ID, other);

        handler.handleTextMessage(viewer, qaUpdateMessage(QA_ID, UPDATE_B64));

        assertThat(qaUpdateBuffer.currentQaId(DOC_ID)).isEmpty();
        verify(other, never()).sendMessage(any());
        verify(feedbackRepository, never()).findById(any());
    }

    @Test
    void qaUpdate_withQuestioningQa_isBufferedAndBroadcastToOthersOnly() throws Exception {
        WebSocketSession sender = openSession("s-sender", MemberRole.MEMBER);
        WebSocketSession other = openSession("s-other", MemberRole.MEMBER);
        registerSession(DOC_ID, sender);
        registerSession(DOC_ID, other);
        Feedback feedback = feedbackWith(QA_ID, DOC_ID, FeedbackStatus.QUESTIONING);
        when(feedbackRepository.findById(QA_ID)).thenReturn(Optional.of(feedback));

        handler.handleTextMessage(sender, qaUpdateMessage(QA_ID, UPDATE_B64));

        assertThat(qaUpdateBuffer.snapshot(DOC_ID, QA_ID)).hasSize(1);
        verify(sender, never()).sendMessage(any());
        JsonNode sent = lastSentJson(other);
        assertThat(sent.get("type").asText()).isEqualTo("qa:update");
        assertThat(sent.get("qaId").asText()).isEqualTo(QA_ID);
        assertThat(sent.get("update").asText()).isEqualTo(UPDATE_B64);
    }

    @Test
    void qaUpdate_withQuestioningDraft_isBuffered() throws Exception {
        WebSocketSession sender = openSession("s-sender", MemberRole.OWNER);
        registerSession(DOC_ID, sender);
        when(feedbackRepository.findById(QA_ID)).thenReturn(Optional.empty());
        Draft draft = draftWith(QA_ID, DraftStatus.QUESTIONING);
        when(draftRepository.findByDocumentId(DOC_ID)).thenReturn(Optional.of(draft));

        handler.handleTextMessage(sender, qaUpdateMessage(QA_ID, UPDATE_B64));

        assertThat(qaUpdateBuffer.snapshot(DOC_ID, QA_ID)).hasSize(1);
    }

    @Test
    void qaUpdate_withNonQuestioningQa_isDroppedSilently() throws Exception {
        WebSocketSession sender = openSession("s-sender", MemberRole.MEMBER);
        WebSocketSession other = openSession("s-other", MemberRole.MEMBER);
        registerSession(DOC_ID, sender);
        registerSession(DOC_ID, other);
        Feedback feedback = feedbackWith(QA_ID, DOC_ID, FeedbackStatus.ANSWERING);
        when(feedbackRepository.findById(QA_ID)).thenReturn(Optional.of(feedback));
        when(draftRepository.findByDocumentId(DOC_ID)).thenReturn(Optional.empty());

        handler.handleTextMessage(sender, qaUpdateMessage(QA_ID, UPDATE_B64));

        assertThat(qaUpdateBuffer.currentQaId(DOC_ID)).isEmpty();
        verify(other, never()).sendMessage(any());
        verify(socketErrorSender, never()).send(any(), any());
    }

    @Test
    void qaUpdate_withFeedbackOfOtherDocument_isDropped() throws Exception {
        WebSocketSession sender = openSession("s-sender", MemberRole.MEMBER);
        registerSession(DOC_ID, sender);
        Feedback feedback = feedbackWith(QA_ID, "other-doc", FeedbackStatus.QUESTIONING);
        when(feedbackRepository.findById(QA_ID)).thenReturn(Optional.of(feedback));
        when(draftRepository.findByDocumentId(DOC_ID)).thenReturn(Optional.empty());

        handler.handleTextMessage(sender, qaUpdateMessage(QA_ID, UPDATE_B64));

        assertThat(qaUpdateBuffer.currentQaId(DOC_ID)).isEmpty();
    }

    @Test
    void qaUpdate_withCachedQaId_doesNotQueryDatabaseAgain() throws Exception {
        WebSocketSession sender = openSession("s-sender", MemberRole.MEMBER);
        registerSession(DOC_ID, sender);
        Feedback feedback = feedbackWith(QA_ID, DOC_ID, FeedbackStatus.QUESTIONING);
        when(feedbackRepository.findById(QA_ID)).thenReturn(Optional.of(feedback));

        handler.handleTextMessage(sender, qaUpdateMessage(QA_ID, UPDATE_B64));
        handler.handleTextMessage(sender, qaUpdateMessage(QA_ID, UPDATE_B64));

        verify(feedbackRepository, times(1)).findById(QA_ID);
        assertThat(qaUpdateBuffer.snapshot(DOC_ID, QA_ID)).hasSize(2);
    }

    @Test
    void qaUpdate_withoutQaId_sendsInvalidMessage() throws Exception {
        WebSocketSession sender = openSession("s-sender", MemberRole.MEMBER);

        handler.handleTextMessage(sender, qaUpdateMessage(null, UPDATE_B64));

        verify(socketErrorSender).send(sender, SocketErrorCode.INVALID_MESSAGE);
        assertThat(qaUpdateBuffer.currentQaId(DOC_ID)).isEmpty();
    }

    @Test
    void qaUpdate_withInvalidBase64_sendsInvalidMessage() throws Exception {
        WebSocketSession sender = openSession("s-sender", MemberRole.MEMBER);

        handler.handleTextMessage(sender, qaUpdateMessage(QA_ID, "not-base64!!"));

        verify(socketErrorSender).send(sender, SocketErrorCode.INVALID_MESSAGE);
    }

    @Test
    void qaUpdate_tooLarge_isDroppedWithoutError() throws Exception {
        WebSocketSession sender = openSession("s-sender", MemberRole.MEMBER);
        String tooLarge = "A".repeat(DocumentWebSocketHandler.MAX_QA_UPDATE_BASE64_LENGTH + 4);

        handler.handleTextMessage(sender, qaUpdateMessage(QA_ID, tooLarge));

        verify(socketErrorSender, never()).send(any(), any());
        verify(feedbackRepository, never()).findById(any());
        assertThat(qaUpdateBuffer.currentQaId(DOC_ID)).isEmpty();
    }

    // ───── doc:init activeQa ─────

    @Test
    void docInit_includesActiveQa_whenFeedbackIsQuestioning() throws Exception {
        WebSocketSession session = openSession("s-1", MemberRole.MEMBER);
        Feedback feedback = feedbackWith(QA_ID, DOC_ID, FeedbackStatus.QUESTIONING);
        when(feedbackRepository.findTopByDocumentIdAndStatusNotInOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.of(feedback));
        qaUpdateBuffer.append(DOC_ID, QA_ID, new byte[]{1, 2, 3});

        handler.afterConnectionEstablished(session);

        JsonNode activeQa = lastSentJson(session).get("activeQa");
        assertThat(activeQa.get("qaId").asText()).isEqualTo(QA_ID);
        assertThat(activeQa.get("updates")).hasSize(1);
        assertThat(activeQa.get("updates").get(0).asText()).isEqualTo(UPDATE_B64);
    }

    @Test
    void docInit_includesActiveQa_whenDraftIsQuestioning() throws Exception {
        WebSocketSession session = openSession("s-1", MemberRole.MEMBER);
        Draft draft = draftWith(QA_ID, DraftStatus.QUESTIONING);
        when(draftRepository.findByDocumentId(DOC_ID)).thenReturn(Optional.of(draft));

        handler.afterConnectionEstablished(session);

        JsonNode activeQa = lastSentJson(session).get("activeQa");
        assertThat(activeQa.get("qaId").asText()).isEqualTo(QA_ID);
        assertThat(activeQa.get("updates")).isEmpty();
    }

    @Test
    void docInit_sendsNullActiveQaAndClearsBuffer_whenNothingIsQuestioning() throws Exception {
        WebSocketSession session = openSession("s-1", MemberRole.MEMBER);
        qaUpdateBuffer.append(DOC_ID, QA_ID, new byte[]{1});

        handler.afterConnectionEstablished(session);

        JsonNode init = lastSentJson(session);
        assertThat(init.has("activeQa")).isTrue();
        assertThat(init.get("activeQa").isNull()).isTrue();
        assertThat(qaUpdateBuffer.currentQaId(DOC_ID)).isEmpty();
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
