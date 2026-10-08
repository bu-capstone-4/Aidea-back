package com.aidea.aidea.domain.aifeedback.service;

import com.aidea.aidea.domain.aifeedback.controller.dto.AnswerRequest;
import com.aidea.aidea.domain.aifeedback.entity.Feedback;
import com.aidea.aidea.domain.aifeedback.entity.FeedbackStatus;
import com.aidea.aidea.domain.aifeedback.repository.FeedbackRepository;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.documents.repository.DocumentUpdateRepository;
import com.aidea.aidea.domain.documents.websocket.QaUpdateBuffer;
import com.aidea.aidea.domain.teamspace.entity.TeamSpace;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import com.aidea.aidea.global.util.TeamspaceRoleValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock private FeedbackRepository feedbackRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentUpdateRepository documentUpdateRepository;
    @Mock private UserRepository userRepository;
    @Mock private GeminiService geminiService;
    @Mock private FeedbackEventPublisher eventPublisher;
    @Mock private TeamspaceRoleValidator roleValidator;
    @Mock private QaUpdateBuffer qaUpdateBuffer;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private FeedbackService service;

    private static final String DOC_ID = "doc-1";
    private static final String FEEDBACK_ID = "fb-1";
    private static final String TEAMSPACE_ID = "ts-1";

    @BeforeEach
    void setUp() {
        service = new FeedbackService(feedbackRepository, documentRepository, documentUpdateRepository, userRepository,
                geminiService, eventPublisher, objectMapper, roleValidator, qaUpdateBuffer);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Feedback feedbackWithStatus(FeedbackStatus status) {
        TeamSpace teamspace = mock(TeamSpace.class);
        when(teamspace.getTeamspaceId()).thenReturn(TEAMSPACE_ID);
        Document doc = mock(Document.class);
        when(doc.getTeamspace()).thenReturn(teamspace);
        lenient().when(doc.getId()).thenReturn(DOC_ID);
        Feedback feedback = Feedback.create(FEEDBACK_ID, doc, null, "", null, null);
        feedback.setStatus(status);
        return feedback;
    }

    private AnswerRequest answerRequest() {
        return new AnswerRequest(List.of(new AnswerRequest.AnswerItem("q1", "답변")));
    }

    @Test
    void submitAnswer_switchesToAnswering_clearsBuffer_andPublishesAnsweringAfterCommit() throws Exception {
        Feedback feedback = feedbackWithStatus(FeedbackStatus.QUESTIONING);
        when(feedbackRepository.findByIdForUpdate(FEEDBACK_ID)).thenReturn(Optional.of(feedback));
        TransactionSynchronizationManager.initSynchronization();

        service.submitAnswer(FEEDBACK_ID, answerRequest(), 1L);

        assertThat(feedback.getStatus()).isEqualTo(FeedbackStatus.ANSWERING);
        verify(qaUpdateBuffer).clear(DOC_ID);
        verify(eventPublisher, never()).publishToDocument(any(), any());

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.get(0).afterCommit();

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher).publishToDocument(eq(DOC_ID), json.capture());
        JsonNode event = objectMapper.readTree(json.getValue());
        assertThat(event.get("type").asText()).isEqualTo("feedback:answering");
        assertThat(event.get("feedbackId").asText()).isEqualTo(FEEDBACK_ID);
        verify(geminiService).callGeminiWithAnswers(FEEDBACK_ID);
    }

    @Test
    void submitAnswer_throwsInvalidStatus_whenNotQuestioning() {
        Feedback feedback = feedbackWithStatus(FeedbackStatus.ANSWERING);
        when(feedbackRepository.findByIdForUpdate(FEEDBACK_ID)).thenReturn(Optional.of(feedback));

        assertThatThrownBy(() -> service.submitAnswer(FEEDBACK_ID, answerRequest(), 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FEEDBACK_INVALID_STATUS);

        verify(qaUpdateBuffer, never()).clear(any());
        verify(eventPublisher, never()).publishToDocument(any(), any());
    }
}
