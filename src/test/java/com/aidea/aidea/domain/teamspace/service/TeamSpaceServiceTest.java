package com.aidea.aidea.domain.teamspace.service;

import com.aidea.aidea.domain.aifeedback.repository.FeedbackRepository;
import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentType;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.documents.repository.DocumentUpdateRepository;
import com.aidea.aidea.domain.draft.repository.DraftRepository;
import com.aidea.aidea.domain.draft.service.DraftService;
import com.aidea.aidea.domain.teamspace.dto.TeamSpaceCreateRequest;
import com.aidea.aidea.domain.teamspace.entity.TeamSpace;
import com.aidea.aidea.domain.teamspace.repository.TeamSpaceRepository;
import com.aidea.aidea.domain.teamspace.repository.TeamspaceMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamSpaceServiceTest {

    @Mock private TeamSpaceRepository teamSpaceRepository;
    @Mock private TeamspaceMemberRepository teamspaceMemberRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentUpdateRepository documentUpdateRepository;
    @Mock private FeedbackRepository feedbackRepository;
    @Mock private DraftRepository draftRepository;
    @Mock private UserRepository userRepository;
    @Mock private DraftService draftService;

    private TeamSpaceService service;

    private static final Long USER_ID = 1L;
    private static final String TEAMSPACE_ID = "ts-1";
    private static final String IDEA_CONTEXT = "아이디어 설명";

    @BeforeEach
    void setUp() {
        service = new TeamSpaceService(
                teamSpaceRepository, teamspaceMemberRepository, documentRepository,
                documentUpdateRepository, feedbackRepository, draftRepository,
                userRepository, draftService
        );
    }

    private User owner() {
        return User.builder()
                .id(USER_ID)
                .name("Test User")
                .provider("github")
                .providerId("12345")
                .githubLogin("testuser")
                .build();
    }

    private TeamSpace savedTeamSpace(User owner) {
        return TeamSpace.builder()
                .teamspaceId(TEAMSPACE_ID)
                .name("My Team")
                .owner(owner)
                .build();
    }

    @Test
    void create_doesNotTriggerAnyDraftForFreeDocument() {
        User owner = owner();
        TeamSpace teamSpace = savedTeamSpace(owner);
        Document freeDoc = Document.create("doc-free", teamSpace, DocumentType.FREE, "새 문서");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(owner));
        when(teamSpaceRepository.save(any())).thenReturn(teamSpace);
        when(documentRepository.saveAll(any())).thenReturn(List.of(freeDoc));

        TeamSpaceCreateRequest request = TeamSpaceCreateRequest.builder()
                .name("My Team")
                .idea(IDEA_CONTEXT)
                .documentTypes(List.of(DocumentType.FREE))
                .build();

        service.create(request, USER_ID);

        verify(draftService, never()).savePendingDraft(any(), any());
        verify(draftService, never()).triggerDraftGeneration(any(), any(), any());
    }

    @Test
    void create_skipsFreeDraft_butTriggersDraftForIdeaAndPlanDocuments() {
        User owner = owner();
        TeamSpace teamSpace = savedTeamSpace(owner);
        Document ideaDoc = Document.create("doc-idea", teamSpace, DocumentType.IDEA, "IDEA");
        Document planDoc = Document.create("doc-plan", teamSpace, DocumentType.PLAN, "PLAN");
        Document freeDoc = Document.create("doc-free", teamSpace, DocumentType.FREE, "새 문서");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(owner));
        when(teamSpaceRepository.save(any())).thenReturn(teamSpace);
        when(documentRepository.saveAll(any())).thenReturn(List.of(ideaDoc, planDoc, freeDoc));

        TeamSpaceCreateRequest request = TeamSpaceCreateRequest.builder()
                .name("My Team")
                .idea(IDEA_CONTEXT)
                .documentTypes(List.of(DocumentType.IDEA, DocumentType.PLAN, DocumentType.FREE))
                .build();

        service.create(request, USER_ID);

        // IDEA → triggerDraftGeneration
        verify(draftService).triggerDraftGeneration(eq("doc-idea"), eq(IDEA_CONTEXT), eq("My Team"));
        // PLAN → savePendingDraft
        verify(draftService).savePendingDraft(eq("doc-plan"), eq(IDEA_CONTEXT));
        // FREE → 아무 것도 호출되지 않아야 함
        verify(draftService, never()).savePendingDraft(eq("doc-free"), any());
        verify(draftService, never()).triggerDraftGeneration(eq("doc-free"), any(), any());
    }

    @Test
    void create_withEmptyDocumentTypes_doesNotCallDraftService() {
        User owner = owner();
        TeamSpace teamSpace = savedTeamSpace(owner);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(owner));
        when(teamSpaceRepository.save(any())).thenReturn(teamSpace);

        TeamSpaceCreateRequest request = TeamSpaceCreateRequest.builder()
                .name("My Team")
                .idea(null)
                .documentTypes(List.of())
                .build();

        service.create(request, USER_ID);

        verify(draftService, never()).savePendingDraft(any(), any());
        verify(draftService, never()).triggerDraftGeneration(any(), any(), any());
        verify(documentRepository, never()).saveAll(any());
    }
}
