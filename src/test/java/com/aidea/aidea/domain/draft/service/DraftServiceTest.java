package com.aidea.aidea.domain.draft.service;

import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentType;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.draft.entity.DraftStatus;
import com.aidea.aidea.domain.draft.repository.DraftRepository;
import com.aidea.aidea.global.util.TeamspaceRoleValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
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

    private DraftService service;

    private static final String DOC_ID = "doc-1";
    private static final String IDEA_CONTEXT = "아이디어 설명";
    private static final String TEAMSPACE_NAME = "My Team";

    @BeforeEach
    void setUp() {
        service = new DraftService(draftRepository, documentRepository, draftAsyncExecutor, roleValidator);
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
