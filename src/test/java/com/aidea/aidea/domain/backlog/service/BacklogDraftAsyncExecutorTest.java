package com.aidea.aidea.domain.backlog.service;

import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.backlog.entity.BacklogConfig;
import com.aidea.aidea.domain.backlog.entity.IssueType;
import com.aidea.aidea.domain.backlog.entity.Priority;
import com.aidea.aidea.domain.backlog.entity.Story;
import com.aidea.aidea.domain.backlog.entity.Task;
import com.aidea.aidea.domain.backlog.repository.BacklogDraftRepository;
import com.aidea.aidea.domain.backlog.repository.EpicRepository;
import com.aidea.aidea.domain.backlog.repository.StoryRepository;
import com.aidea.aidea.domain.backlog.repository.TaskRepository;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.documents.repository.DocumentUpdateRepository;
import com.aidea.aidea.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BacklogDraftAsyncExecutorTest {

    @Mock
    private BacklogDraftRepository backlogDraftRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentUpdateRepository documentUpdateRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EpicRepository epicRepository;
    @Mock
    private StoryRepository storyRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private BacklogEventPublisher eventPublisher;

    private BacklogDraftAsyncExecutor executor;

    private static final String TEAMSPACE_ID = "ts-1";
    private static final Long USER_ID = 1L;
    private User actor;

    @BeforeEach
    void setUp() {
        executor = new BacklogDraftAsyncExecutor(backlogDraftRepository, documentRepository, documentUpdateRepository,
                userRepository, epicRepository, storyRepository, taskRepository, eventPublisher, new ObjectMapper());

        actor = User.builder()
                .id(USER_ID)
                .provider("github")
                .providerId("p-1")
                .githubLogin("login")
                .build();
    }

    @Test
    void storyDisabled_createsStandaloneTasksOnlyAndNoEpicOrStory() {
        BacklogConfig config = BacklogConfig.create(TEAMSPACE_ID, true, true, false, true, true, true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(actor));
        when(taskRepository.findMaxStandaloneNumberByTeamspaceId(TEAMSPACE_ID)).thenReturn(0L);
        when(taskRepository.findStandaloneByTeamspaceId(TEAMSPACE_ID)).thenReturn(List.of());
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        BacklogDraftAsyncExecutor.TaskDraft taskDraft =
                new BacklogDraftAsyncExecutor.TaskDraft("Task 1", null, "HIGH", "FE", "Sprint 1", "2026-07-01");
        BacklogDraftAsyncExecutor.BacklogDraftResult result =
                new BacklogDraftAsyncExecutor.BacklogDraftResult(null, null, List.of(taskDraft));

        BacklogDraftAsyncExecutor.BacklogDraftSummary summary =
                executor.persistBacklogItems(TEAMSPACE_ID, USER_ID, config, result);

        assertThat(summary.epicCount()).isEqualTo(0);
        assertThat(summary.storyCount()).isEqualTo(0);
        assertThat(summary.taskCount()).isEqualTo(1);
        verify(epicRepository, never()).save(any());
        verify(storyRepository, never()).save(any());
    }

    @Test
    void epicDisabled_createsStoriesAndTasksWithoutEpics() {
        BacklogConfig config = BacklogConfig.create(TEAMSPACE_ID, true, false, true, true, true, true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(actor));
        when(storyRepository.findMaxNumberByTeamspaceId(TEAMSPACE_ID)).thenReturn(0L);
        when(storyRepository.findAllWithRelationsByTeamspaceId(TEAMSPACE_ID)).thenReturn(List.of());
        when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        BacklogDraftAsyncExecutor.TaskDraft taskDraft =
                new BacklogDraftAsyncExecutor.TaskDraft("Task 1", "Story 1", "HIGH", "FE", "Sprint 1", "2026-07-01");
        BacklogDraftAsyncExecutor.StoryDraft storyDraft = new BacklogDraftAsyncExecutor.StoryDraft(
                "Story 1", "body", Priority.MEDIUM, IssueType.BE, "Sprint 1", "2026-07-01",
                List.of("Some Epic"));
        BacklogDraftAsyncExecutor.BacklogDraftResult result = new BacklogDraftAsyncExecutor.BacklogDraftResult(
                List.of(new BacklogDraftAsyncExecutor.EpicDraft("Epic 1", "#123456", "desc")),
                List.of(storyDraft), List.of(taskDraft));

        BacklogDraftAsyncExecutor.BacklogDraftSummary summary =
                executor.persistBacklogItems(TEAMSPACE_ID, USER_ID, config, result);

        assertThat(summary.epicCount()).isEqualTo(0);
        assertThat(summary.storyCount()).isEqualTo(1);
        assertThat(summary.taskCount()).isEqualTo(1);
        verify(epicRepository, never()).save(any());
    }

    @Test
    void disabledFields_areNulledOnPersistedEntities() {
        // priority/feBe/sprint/dueDate 모두 비활성화, story만 활성화
        BacklogConfig config = BacklogConfig.create(TEAMSPACE_ID, false, false, true, false, false, false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(actor));
        when(storyRepository.findMaxNumberByTeamspaceId(TEAMSPACE_ID)).thenReturn(0L);
        when(storyRepository.findAllWithRelationsByTeamspaceId(TEAMSPACE_ID)).thenReturn(List.of());

        ArgumentCaptor<Story> storyCaptor = ArgumentCaptor.forClass(Story.class);
        when(storyRepository.save(storyCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        when(taskRepository.save(taskCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        BacklogDraftAsyncExecutor.TaskDraft taskDraft =
                new BacklogDraftAsyncExecutor.TaskDraft("Task 1", "Story 1", "HIGH", "FE", "Sprint 1", "2026-07-01");
        BacklogDraftAsyncExecutor.StoryDraft storyDraft = new BacklogDraftAsyncExecutor.StoryDraft(
                "Story 1", "body", Priority.MEDIUM, IssueType.BE, "Sprint 1", "2026-07-01", null);
        BacklogDraftAsyncExecutor.BacklogDraftResult result =
                new BacklogDraftAsyncExecutor.BacklogDraftResult(null, List.of(storyDraft), List.of(taskDraft));

        executor.persistBacklogItems(TEAMSPACE_ID, USER_ID, config, result);

        Story savedStory = storyCaptor.getValue();
        assertThat(savedStory.getPriority()).isNull();
        assertThat(savedStory.getIssueType()).isNull();
        assertThat(savedStory.getSprint()).isNull();
        assertThat(savedStory.getDueDate()).isNull();

        Task savedTask = taskCaptor.getValue();
        assertThat(savedTask.getIssueType()).isNull();
    }

    @Test
    void classifyException_geminiInvalidResponse_mapsToInvalidResponseErrorCode() {
        BacklogDraftAsyncExecutor.GeminiInvalidResponseException ex =
                new BacklogDraftAsyncExecutor.GeminiInvalidResponseException("Gemini가 백로그 항목을 생성하지 않음");

        ErrorCode errorCode = executor.classifyException(ex);

        assertThat(errorCode).isEqualTo(ErrorCode.BACKLOG_DRAFT_GEMINI_INVALID_RESPONSE);
    }

    @Test
    void buildBacklogDraftResponseSchema_storyDisabled_onlyContainsTasks() {
        BacklogConfig config = BacklogConfig.create(TEAMSPACE_ID, true, true, false, true, true, true);

        @SuppressWarnings("unchecked")
        Map<String, Object> schema = executor.buildBacklogDraftResponseSchema(config);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        assertThat(properties).containsOnlyKeys("tasks");
        assertThat(schema.get("required")).isEqualTo(List.of("tasks"));
    }
}
