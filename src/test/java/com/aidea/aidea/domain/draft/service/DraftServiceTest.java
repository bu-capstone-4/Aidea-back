package com.aidea.aidea.domain.draft.service;

import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentType;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.documents.websocket.QaUpdateBuffer;
import com.aidea.aidea.domain.draft.controller.dto.DraftAnswerRequest;
import com.aidea.aidea.domain.draft.entity.Draft;
import com.aidea.aidea.domain.draft.entity.DraftStatus;
import com.aidea.aidea.domain.draft.repository.DraftRepository;
import com.aidea.aidea.domain.teamspace.entity.TeamSpace;
import com.aidea.aidea.domain.teamspace.service.TeamspaceEventPublisher;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import com.aidea.aidea.global.util.TeamspaceRoleValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DraftServiceTest {

    @Mock private DraftRepository draftRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private DraftAsyncExecutor draftAsyncExecutor;
    @Mock private TeamspaceRoleValidator roleValidator;
    @Mock private TeamspaceEventPublisher teamspaceEventPublisher;
    @Mock private QaUpdateBuffer qaUpdateBuffer;

    private DraftService service;

    private static final String DOC_ID = "doc-1";
    private static final String DRAFT_ID = "draft-1";
    private static final String TEAMSPACE_ID = "ts-1";
    private static final String IDEA_CONTEXT = "아이디어 설명";
    private static final String TEAMSPACE_NAME = "My Team";

    @BeforeEach
    void setUp() {
        service = new DraftService(draftRepository, documentRepository, draftAsyncExecutor, roleValidator,
                teamspaceEventPublisher, qaUpdateBuffer);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Draft draftWithStatus(DraftStatus status) {
        TeamSpace teamspace = mock(TeamSpace.class);
        when(teamspace.getTeamspaceId()).thenReturn(TEAMSPACE_ID);
        Document doc = mock(Document.class);
        when(doc.getTeamspace()).thenReturn(teamspace);
        lenient().when(doc.getId()).thenReturn(DOC_ID);
        lenient().when(teamspace.getName()).thenReturn(TEAMSPACE_NAME);
        Draft draft = Draft.create(DRAFT_ID, doc, IDEA_CONTEXT);
        draft.setStatus(status);
        return draft;
    }

    // ───── submitDraftAnswer ─────

    @Test
    void submitDraftAnswer_clearsQaBufferAndPublishesAnsweringAfterCommit() {
        Draft draft = draftWithStatus(DraftStatus.QUESTIONING);
        when(draftRepository.findByIdForUpdate(DRAFT_ID)).thenReturn(Optional.of(draft));
        TransactionSynchronizationManager.initSynchronization();

        service.submitDraftAnswer(DRAFT_ID, new DraftAnswerRequest(List.of(
                new DraftAnswerRequest.AnswerItem("q1", "답변"))), 1L);

        verify(qaUpdateBuffer).clear(DOC_ID);
        verify(teamspaceEventPublisher, never()).publishDraftAnswering(any(), any(), any());

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.get(0).afterCommit();

        verify(teamspaceEventPublisher).publishDraftAnswering(TEAMSPACE_ID, DOC_ID, DRAFT_ID);
        verify(draftAsyncExecutor).generateFinalIdeaDraft(DRAFT_ID, TEAMSPACE_ID, TEAMSPACE_NAME);
    }

    @Test
    void submitDraftAnswer_throwsAndPublishesNothing_whenNotQuestioning() {
        Draft draft = draftWithStatus(DraftStatus.ANSWERING);
        when(draftRepository.findByIdForUpdate(DRAFT_ID)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.submitDraftAnswer(DRAFT_ID, new DraftAnswerRequest(List.of()), 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DRAFT_INVALID_STATUS);

        verify(qaUpdateBuffer, never()).clear(any());
        verify(teamspaceEventPublisher, never()).publishDraftAnswering(any(), any(), any());
    }

    // ───── savePendingDraft ─────

    @Test
    void savePendingDraft_skipsDraftCreation_forFreeType() {
        Document doc = mock(Document.class);
        when(doc.getType()).thenReturn(DocumentType.FREE);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));

        service.savePendingDraft(DOC_ID, IDEA_CONTEXT);

        // 존재 여부 체크도, save 도 호출되지 않아야 한다
        verify(draftRepository, never()).existsByDocumentIdAndStatus(any(), any());
        verify(draftRepository, never()).save(any());
    }

    @Test
    void savePendingDraft_skipsDraftCreation_whenAlreadyPending() {
        Document doc = mock(Document.class);
        when(doc.getType()).thenReturn(DocumentType.PLAN);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(draftRepository.existsByDocumentIdAndStatus(DOC_ID, DraftStatus.PENDING)).thenReturn(true);

        service.savePendingDraft(DOC_ID, IDEA_CONTEXT);

        verify(draftRepository, never()).save(any());
    }

    @Test
    void savePendingDraft_savesDraft_whenNonFreeTypeAndNotPending() {
        Document doc = mock(Document.class);
        when(doc.getType()).thenReturn(DocumentType.PLAN);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(draftRepository.existsByDocumentIdAndStatus(DOC_ID, DraftStatus.PENDING)).thenReturn(false);

        service.savePendingDraft(DOC_ID, IDEA_CONTEXT);

        verify(draftRepository).save(any());
    }

    // ───── triggerDraftGeneration ─────

    @Test
    void triggerDraftGeneration_skipsDraftCreation_forFreeType() {
        Document doc = mock(Document.class);
        when(doc.getType()).thenReturn(DocumentType.FREE);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));

        service.triggerDraftGeneration(DOC_ID, IDEA_CONTEXT, TEAMSPACE_NAME);

        verify(draftRepository, never()).existsByDocumentIdAndStatus(any(), any());
        verify(draftRepository, never()).save(any());
        verify(draftAsyncExecutor, never()).generateDraftAsync(any(), any(), any());
    }

    @Test
    void triggerDraftGeneration_skipsDraftCreation_whenAlreadyPending() {
        Document doc = mock(Document.class);
        when(doc.getType()).thenReturn(DocumentType.IDEA);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(draftRepository.existsByDocumentIdAndStatus(DOC_ID, DraftStatus.PENDING)).thenReturn(true);

        service.triggerDraftGeneration(DOC_ID, IDEA_CONTEXT, TEAMSPACE_NAME);

        verify(draftRepository, never()).save(any());
        verify(draftAsyncExecutor, never()).generateDraftAsync(any(), any(), any());
    }
}
