package com.aidea.aidea.domain.backlog.service;

import com.aidea.aidea.domain.backlog.entity.BacklogConfig;
import com.aidea.aidea.domain.backlog.entity.BacklogDraftStatus;
import com.aidea.aidea.domain.backlog.repository.BacklogDraftRepository;
import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentAiStatus;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.teamspace.repository.TeamSpaceRepository;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BacklogDraftServiceTest {

    @Mock
    private BacklogDraftRepository backlogDraftRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private TeamSpaceRepository teamSpaceRepository;
    @Mock
    private BacklogDraftAsyncExecutor backlogDraftAsyncExecutor;

    private BacklogDraftService service;

    private static final String TEAMSPACE_ID = "ts-1";
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new BacklogDraftService(backlogDraftRepository, documentRepository, teamSpaceRepository,
                backlogDraftAsyncExecutor);
    }

    @Test
    void documentInDraftStatus_throwsBlockedByDocumentDraft() {
        Document document = mock(Document.class);
        when(document.getStatus()).thenReturn(DocumentAiStatus.DRAFT);
        when(documentRepository.findByTeamspaceId(TEAMSPACE_ID)).thenReturn(List.of(document));

        BacklogConfig config = BacklogConfig.create(TEAMSPACE_ID, false, false, false, false, false, false);

        assertThatThrownBy(() -> service.triggerBacklogDraftGeneration(TEAMSPACE_ID, USER_ID, config))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.BACKLOG_DRAFT_BLOCKED_BY_DOCUMENT_DRAFT);
    }

    @Test
    void noDocuments_throwsNoPlanningDocument() {
        when(documentRepository.findByTeamspaceId(TEAMSPACE_ID)).thenReturn(List.of());

        BacklogConfig config = BacklogConfig.create(TEAMSPACE_ID, false, false, false, false, false, false);

        assertThatThrownBy(() -> service.triggerBacklogDraftGeneration(TEAMSPACE_ID, USER_ID, config))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.BACKLOG_DRAFT_NO_PLANNING_DOCUMENT);
    }

    @Test
    void existingPendingDraft_throwsAlreadyInProgress() {
        Document document = mock(Document.class);
        when(document.getStatus()).thenReturn(DocumentAiStatus.IDLE);
        when(documentRepository.findByTeamspaceId(TEAMSPACE_ID)).thenReturn(List.of(document));
        when(backlogDraftRepository.existsByTeamspaceIdAndStatus(TEAMSPACE_ID, BacklogDraftStatus.PENDING))
                .thenReturn(true);

        BacklogConfig config = BacklogConfig.create(TEAMSPACE_ID, false, false, false, false, false, false);

        assertThatThrownBy(() -> service.triggerBacklogDraftGeneration(TEAMSPACE_ID, USER_ID, config))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.BACKLOG_DRAFT_ALREADY_IN_PROGRESS);
    }
}
