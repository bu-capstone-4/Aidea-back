package com.aidea.aidea.domain.backlog.service;

import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.backlog.entity.BacklogConfig;
import com.aidea.aidea.domain.backlog.entity.BacklogDraft;
import com.aidea.aidea.domain.backlog.entity.Epic;
import com.aidea.aidea.domain.backlog.entity.IssueType;
import com.aidea.aidea.domain.backlog.entity.Priority;
import com.aidea.aidea.domain.backlog.entity.Story;
import com.aidea.aidea.domain.backlog.entity.StoryEpic;
import com.aidea.aidea.domain.backlog.entity.StoryStatus;
import com.aidea.aidea.domain.backlog.entity.Task;
import com.aidea.aidea.domain.backlog.repository.BacklogDraftRepository;
import com.aidea.aidea.domain.backlog.repository.EpicRepository;
import com.aidea.aidea.domain.backlog.repository.StoryRepository;
import com.aidea.aidea.domain.backlog.repository.TaskRepository;
import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentUpdate;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.documents.repository.DocumentUpdateRepository;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import com.aidea.aidea.global.util.YjsTextExtractor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacklogDraftAsyncExecutor {

    private static final String DEFAULT_EPIC_COLOR = "#6366F1";
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final BacklogDraftRepository backlogDraftRepository;
    private final DocumentRepository documentRepository;
    private final DocumentUpdateRepository documentUpdateRepository;
    private final UserRepository userRepository;
    private final EpicRepository epicRepository;
    private final StoryRepository storyRepository;
    private final TaskRepository taskRepository;
    private final BacklogEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api-key}")  private String apiKey;
    @Value("${gemini.base-url}") private String geminiBaseUrl;
    @Value("${gemini.model}")    private String geminiModel;

    private final RestClient restClient = createRestClient();

    private static RestClient createRestClient() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()
        );
        requestFactory.setReadTimeout(Duration.ofSeconds(120));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 03단계 진입점: 기획 문서 수집 -> 프롬프트/스키마 구성 -> Gemini 호출 -> 결과 파싱.
     * 영속화(Epic/Story/Task 저장)와 이벤트 발행은 04단계에서 추가된다.
     */
    @Async
    @Transactional
    public void generateBacklogDraftAsync(String backlogDraftId, String teamspaceId,
                                           String teamspaceName, BacklogConfig config, Long actorUserId) {
        log.warn("[BACKLOG_DRAFT] generateBacklogDraftAsync started backlogDraftId={} teamspaceId={} thread={}",
                backlogDraftId, teamspaceId, Thread.currentThread().getName());

        BacklogDraft draft = backlogDraftRepository.findById(backlogDraftId)
                .orElseThrow(() -> new IllegalStateException("backlog draft not found: " + backlogDraftId));

        try {
            String context = buildPlanningContext(teamspaceId);
            if (context.isBlank()) {
                throw new GeminiInvalidResponseException("기획 문서 내용 없음");
            }

            String prompt = buildBacklogDraftPrompt(context, teamspaceName, config);
            Map<String, Object> schema = buildBacklogDraftResponseSchema(config);
            BacklogDraftResult result = callWithRetry(() -> executeBacklogDraftRequest(prompt, schema));

            log.warn("[BACKLOG_DRAFT] parsed result epics={} stories={} tasks={}",
                    result.epics() == null ? 0 : result.epics().size(),
                    result.stories() == null ? 0 : result.stories().size(),
                    result.tasks() == null ? 0 : result.tasks().size());

            BacklogDraftSummary summary = persistBacklogItems(teamspaceId, actorUserId, config, result);

            draft.markDone();
            publishBacklogDraftReady(teamspaceId, actorUserId, summary);
        } catch (Exception e) {
            ErrorCode errorCode = classifyException(e);
            log.error("[BACKLOG_DRAFT] generation failed backlogDraftId={} errorCode={}", backlogDraftId, errorCode.getCode(), e);
            draft.markFailed(errorCode.getCode(), truncate(e.getMessage(), 500));
            publishBacklogDraftError(teamspaceId, actorUserId, errorCode.getCode());
        }
    }

    private String buildPlanningContext(String teamspaceId) {
        List<Document> documents = documentRepository.findByTeamspaceId(teamspaceId);
        StringBuilder context = new StringBuilder();
        for (Document document : documents) {
            List<byte[]> updates = documentUpdateRepository.findByDocumentIdOrderByIdAsc(document.getId())
                    .stream().map(DocumentUpdate::getUpdateBinary).toList();
            String markdown = YjsTextExtractor.extractText(document.getYjsSnapshot(), updates);
            if (markdown.isBlank()) {
                continue;
            }
            context.append("## ").append(document.getType().displayName()).append("\n")
                    .append(markdown).append("\n\n");
        }
        return context.toString();
    }

    private String buildBacklogDraftPrompt(String planningContext, String teamspaceName, BacklogConfig config) {
        String projectSection = (teamspaceName != null && !teamspaceName.isBlank())
                ? "[PROJECT NAME]\n" + teamspaceName + "\n\n"
                : "";

        StringBuilder rules = new StringBuilder();
        rules.append("- ").append(config.isStoryEnabled()
                ? "Story(사용자 스토리) 단위로 작업을 묶어. Task는 stories와 별도로 최상위 tasks 배열에 작성하고, 각 Task의 storyTitle 필드에 소속될 Story의 title과 정확히 동일한 문자열을 입력해."
                : "Story 없이 독립적인 Task만 생성해.").append("\n");
        rules.append("- ").append(config.isEpicEnabled() && config.isStoryEnabled()
                ? "관련된 Story들을 Epic(주요 기능 단위)으로 그룹핑해."
                : "Epic은 생성하지 마.").append("\n");
        rules.append("- ").append(config.isFeBeEnabled()
                ? "각 Task/Story에 FE 또는 BE 중 하나를 issueType으로 지정해."
                : "issueType 필드는 생성하지 마.").append("\n");
        rules.append("- ").append(config.isPriorityEnabled()
                ? "각 항목에 LOW/MEDIUM/HIGH/URGENT 중 하나를 priority로 지정해."
                : "priority 필드는 생성하지 마.").append("\n");
        rules.append("- ").append(config.isSprintEnabled()
                ? "관련 작업을 'Sprint 1', 'Sprint 2'... 형태의 sprint로 묶어줘."
                : "sprint 필드는 생성하지 마.").append("\n");
        rules.append("- ").append(config.isDueDateEnabled()
                ? "각 항목에 합리적인 dueDate(YYYY-MM-DD)를 추정해서 넣어줘."
                : "dueDate 필드는 생성하지 마.");

        return """
                [ROLE] 너는 IT 스타트업의 프로젝트 매니저야.
                [TASK] 아래 기획 문서들을 분석해서 이 프로젝트의 백로그(작업 목록)를 만들어줘.

                %s[PLANNING DOCUMENTS]
                %s

                [BACKLOG STRUCTURE RULES]
                %s

                [OUTPUT]
                반드시 JSON 형식. 다른 형식 절대 안 됨.
                """.formatted(projectSection, planningContext, rules);
    }

    // ===== 응답 스키마 동적 구성 =====

    /**
     * Gemini structured output 제약(상태 폭발) 완화를 위해, 중첩 배열 안에서 반복되는
     * 필드(task)는 enum 없이 STRING으로 받고 영속화 시점에 파싱/검증한다.
     */
    private Map<String, Object> commonItemFields(BacklogConfig config, boolean useEnums) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (config.isPriorityEnabled()) {
            fields.put("priority", useEnums
                    ? Map.of("type", "STRING", "enum", List.of("LOW", "MEDIUM", "HIGH", "URGENT"))
                    : Map.of("type", "STRING"));
        }
        if (config.isFeBeEnabled()) {
            fields.put("issueType", useEnums
                    ? Map.of("type", "STRING", "enum", List.of("FE", "BE"))
                    : Map.of("type", "STRING"));
        }
        if (config.isSprintEnabled()) {
            fields.put("sprint", Map.of("type", "STRING"));
        }
        if (config.isDueDateEnabled()) {
            fields.put("dueDate", Map.of("type", "STRING"));
        }
        return fields;
    }

    /**
     * storyEnabled=true일 때는 task가 stories와 별도의 최상위 배열로 분리되므로,
     * 소속 Story를 가리키는 storyTitle 필드를 함께 받는다 (중첩 배열 깊이를 줄여
     * "too many states" 에러를 회피하기 위함).
     */
    private Map<String, Object> taskSchema(BacklogConfig config, boolean includeStoryTitle) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", Map.of("type", "STRING"));
        if (includeStoryTitle) {
            props.put("storyTitle", Map.of("type", "STRING"));
        }
        props.putAll(commonItemFields(config, false));
        List<String> required = includeStoryTitle ? List.of("title", "storyTitle") : List.of("title");
        return Map.of(
                "type", "OBJECT",
                "properties", props,
                "required", required
        );
    }

    private Map<String, Object> storySchema(BacklogConfig config) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", Map.of("type", "STRING"));
        props.put("body", Map.of("type", "STRING"));
        props.putAll(commonItemFields(config, true));
        if (config.isEpicEnabled()) {
            props.put("epicNames", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"), "maxItems", 3));
        }
        return Map.of(
                "type", "OBJECT",
                "properties", props,
                "required", List.of("title")
        );
    }

    private Map<String, Object> epicSchema() {
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "name", Map.of("type", "STRING"),
                        "color", Map.of("type", "STRING"),
                        "description", Map.of("type", "STRING")
                ),
                "required", List.of("name")
        );
    }

    Map<String, Object> buildBacklogDraftResponseSchema(BacklogConfig config) {
        if (!config.isStoryEnabled()) {
            return Map.of(
                    "type", "OBJECT",
                    "properties", Map.of(
                            "tasks", Map.of(
                                    "type", "ARRAY",
                                    "items", taskSchema(config, false),
                                    "minItems", 4,
                                    "maxItems", 15
                            )
                    ),
                    "required", List.of("tasks")
            );
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("stories", Map.of(
                "type", "ARRAY",
                "items", storySchema(config),
                "minItems", 4,
                "maxItems", 8
        ));
        props.put("tasks", Map.of(
                "type", "ARRAY",
                "items", taskSchema(config, true),
                "minItems", 4,
                "maxItems", 24
        ));
        if (config.isEpicEnabled()) {
            props.put("epics", Map.of(
                    "type", "ARRAY",
                    "items", epicSchema(),
                    "minItems", 2,
                    "maxItems", 4
            ));
        }
        return Map.of(
                "type", "OBJECT",
                "properties", props,
                "required", List.of("stories", "tasks")
        );
    }

    // ===== Gemini 호출 / 재시도 =====

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 3_000L;

    private <T> T callWithRetry(Callable<T> call) throws Exception {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return call.call();
            } catch (HttpClientErrorException e) {
                throw e; // 4xx는 재시도 불가
            } catch (Exception e) {
                if (attempt < MAX_RETRIES && isRetryableException(e)) {
                    log.warn("[BACKLOG_DRAFT] Gemini 재시도 ({}/{}) {}ms 후... 오류: {}", attempt + 1, MAX_RETRIES, RETRY_DELAY_MS, e.getMessage());
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                } else {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private boolean isRetryableException(Exception e) {
        if (e instanceof HttpServerErrorException httpEx) {
            return httpEx.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE;
        }
        return e instanceof RestClientException;
    }

    @SuppressWarnings("unchecked")
    private BacklogDraftResult executeBacklogDraftRequest(String prompt, Map<String, Object> schema) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", schema,
                        "thinkingConfig", Map.of("thinkingBudget", 2048),
                        "maxOutputTokens", 65536
                )
        );

        String url = geminiBaseUrl + "/models/" + geminiModel + ":generateContent";

        Map response = restClient.post()
                .uri(url)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new GeminiInvalidResponseException("Gemini 빈 응답");
        }

        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) throw new GeminiInvalidResponseException("Gemini content 필드 없음");

        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) throw new GeminiInvalidResponseException("Gemini parts 필드 없음");

        String resultJson = parts.stream()
                .filter(p -> !Boolean.TRUE.equals(p.get("thought")))
                .map(p -> (String) p.get("text"))
                .filter(t -> t != null && !t.isBlank())
                .findFirst()
                .orElseThrow(() -> new GeminiInvalidResponseException("Gemini text 파트 없음"));

        BacklogDraftResult result = objectMapper.readValue(resultJson, BacklogDraftResult.class);

        boolean empty = (result.epics() == null || result.epics().isEmpty())
                && (result.stories() == null || result.stories().isEmpty())
                && (result.tasks() == null || result.tasks().isEmpty());
        if (empty) {
            throw new GeminiInvalidResponseException("Gemini가 백로그 항목을 생성하지 않음");
        }

        return result;
    }

    ErrorCode classifyException(Exception e) {
        if (e instanceof HttpClientErrorException httpEx) {
            if (httpEx.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                return ErrorCode.DRAFT_QUOTA_EXCEEDED;
            }
            return ErrorCode.BACKLOG_DRAFT_GEMINI_API_ERROR;
        }
        if (e instanceof HttpServerErrorException || e instanceof RestClientException) {
            return ErrorCode.BACKLOG_DRAFT_GEMINI_API_ERROR;
        }
        if (e instanceof GeminiInvalidResponseException) {
            return ErrorCode.BACKLOG_DRAFT_GEMINI_INVALID_RESPONSE;
        }
        return ErrorCode.BACKLOG_DRAFT_GENERATION_FAILED;
    }

    private String truncate(String message, int maxLength) {
        if (message == null) return null;
        return message.length() > maxLength ? message.substring(0, maxLength) : message;
    }

    static class GeminiInvalidResponseException extends RuntimeException {
        GeminiInvalidResponseException(String message) {
            super(message);
        }
    }

    // ===== 영속화 =====

    BacklogDraftSummary persistBacklogItems(String teamspaceId, Long actorUserId,
                                                      BacklogConfig config, BacklogDraftResult result) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Map<String, Epic> epicByName = new LinkedHashMap<>();
        int epicCount = 0;
        int storyCount = 0;
        int taskCount = 0;

        if (config.isEpicEnabled() && config.isStoryEnabled() && result.epics() != null) {
            long epicNumber = epicRepository.findMaxNumberByTeamspaceId(teamspaceId);
            int epicPosition = currentMaxEpicPosition(teamspaceId);
            for (EpicDraft e : result.epics()) {
                Epic epic = Epic.create(teamspaceId, ++epicNumber, e.name(), normalizeColor(e.color()),
                        e.description(), null, null, null, actor, null, epicPosition += 1000);
                epicByName.put(e.name(), epicRepository.save(epic));
                epicCount++;
            }
        }

        if (config.isStoryEnabled() && result.stories() != null) {
            long storyNumber = storyRepository.findMaxNumberByTeamspaceId(teamspaceId);
            int storyPosition = currentMaxStoryPosition(teamspaceId);
            Map<String, Story> storyByTitle = new LinkedHashMap<>();
            for (StoryDraft s : result.stories()) {
                Story story = Story.create(++storyNumber, teamspaceId, s.title(), s.body(),
                        StoryStatus.OPEN,
                        config.isPriorityEnabled() ? s.priority() : null,
                        config.isFeBeEnabled() ? s.issueType() : null,
                        config.isSprintEnabled() ? s.sprint() : null,
                        null, actor,
                        config.isDueDateEnabled() ? parseDueDate(s.dueDate()) : null,
                        storyPosition += 1000);

                if (config.isEpicEnabled() && s.epicNames() != null) {
                    for (String epicName : s.epicNames()) {
                        Epic epic = epicByName.get(epicName);
                        if (epic != null) {
                            story.getStoryEpics().add(StoryEpic.create(story, epic));
                        }
                    }
                }

                Story savedStory = storyRepository.save(story);
                storyCount++;
                storyByTitle.put(s.title(), savedStory);
            }

            if (result.tasks() != null) {
                long taskNumber = taskRepository.findMaxStandaloneNumberByTeamspaceId(teamspaceId);
                int taskPosition = currentMaxStandaloneTaskPosition(teamspaceId);
                for (TaskDraft t : result.tasks()) {
                    Story story = storyByTitle.get(t.storyTitle());
                    if (story == null) {
                        log.warn("[BACKLOG_DRAFT] task의 storyTitle과 일치하는 Story 없음 storyTitle={} taskTitle={}",
                                t.storyTitle(), t.title());
                        continue;
                    }
                    Task task = Task.createStandalone(teamspaceId, ++taskNumber, t.title(),
                            StoryStatus.OPEN,
                            config.isPriorityEnabled() ? parsePriority(t.priority()) : null,
                            config.isFeBeEnabled() ? parseIssueType(t.issueType()) : null,
                            config.isSprintEnabled() ? t.sprint() : null,
                            null, actor,
                            config.isDueDateEnabled() ? parseDueDate(t.dueDate()) : null,
                            taskPosition += 1000);
                    task.linkToStory(story);
                    taskRepository.save(task);
                    taskCount++;
                }
            }
        } else if (result.tasks() != null) {
            long taskNumber = taskRepository.findMaxStandaloneNumberByTeamspaceId(teamspaceId);
            int taskPosition = currentMaxStandaloneTaskPosition(teamspaceId);
            for (TaskDraft t : result.tasks()) {
                Task task = Task.createStandalone(teamspaceId, ++taskNumber, t.title(),
                        StoryStatus.OPEN,
                        config.isPriorityEnabled() ? parsePriority(t.priority()) : null,
                        config.isFeBeEnabled() ? parseIssueType(t.issueType()) : null,
                        config.isSprintEnabled() ? t.sprint() : null,
                        null, actor,
                        config.isDueDateEnabled() ? parseDueDate(t.dueDate()) : null,
                        taskPosition += 1000);
                taskRepository.save(task);
                taskCount++;
            }
        }

        return new BacklogDraftSummary(epicCount, storyCount, taskCount);
    }

    private int currentMaxEpicPosition(String teamspaceId) {
        List<Epic> epics = epicRepository.findAllWithCreatorByTeamspaceId(teamspaceId);
        return epics.isEmpty() ? 0 : epics.get(epics.size() - 1).getPosition();
    }

    private int currentMaxStoryPosition(String teamspaceId) {
        List<Story> stories = storyRepository.findAllWithRelationsByTeamspaceId(teamspaceId);
        return stories.isEmpty() ? 0 : stories.get(stories.size() - 1).getPosition();
    }

    private int currentMaxStandaloneTaskPosition(String teamspaceId) {
        List<Task> tasks = taskRepository.findStandaloneByTeamspaceId(teamspaceId);
        return tasks.isEmpty() ? 0 : tasks.get(tasks.size() - 1).getPosition();
    }

    private String normalizeColor(String color) {
        return (color != null && HEX_COLOR_PATTERN.matcher(color).matches()) ? color : DEFAULT_EPIC_COLOR;
    }

    private Priority parsePriority(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Priority.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[BACKLOG_DRAFT] priority 파싱 실패 value={}", value);
            return null;
        }
    }

    private IssueType parseIssueType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return IssueType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[BACKLOG_DRAFT] issueType 파싱 실패 value={}", value);
            return null;
        }
    }

    private LocalDate parseDueDate(String dueDate) {
        if (dueDate == null || dueDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dueDate);
        } catch (DateTimeParseException e) {
            log.warn("[BACKLOG_DRAFT] dueDate 파싱 실패 value={}", dueDate);
            return null;
        }
    }

    // ===== WebSocket 이벤트 발행 =====

    private void publishBacklogDraftReady(String teamspaceId, Long actorUserId, BacklogDraftSummary summary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "backlog:draft_ready");
        payload.put("actorId", String.valueOf(actorUserId));
        payload.put("summary", Map.of(
                "epicCount", summary.epicCount(),
                "storyCount", summary.storyCount(),
                "taskCount", summary.taskCount()
        ));
        publishEvent(teamspaceId, actorUserId, payload);
    }

    private void publishBacklogDraftError(String teamspaceId, Long actorUserId, String errorCode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "backlog:draft_error");
        payload.put("actorId", String.valueOf(actorUserId));
        payload.put("errorCode", errorCode);
        publishEvent(teamspaceId, actorUserId, payload);
    }

    private void publishEvent(String teamspaceId, Long actorUserId, Map<String, Object> payload) {
        try {
            eventPublisher.publishToTeamspace(teamspaceId, String.valueOf(actorUserId), objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error("[BACKLOG_DRAFT] failed to publish event teamspaceId={} type={}", teamspaceId, payload.get("type"), e);
        }
    }

    record BacklogDraftSummary(int epicCount, int storyCount, int taskCount) {}

    // ===== 응답 파싱 결과 =====

    record BacklogDraftResult(
            List<EpicDraft> epics,
            List<StoryDraft> stories,
            List<TaskDraft> tasks
    ) {}

    record EpicDraft(String name, String color, String description) {}

    record StoryDraft(String title, String body, Priority priority, IssueType issueType,
                      String sprint, String dueDate, List<String> epicNames) {}

    record TaskDraft(String title, String storyTitle, String priority, String issueType, String sprint, String dueDate) {}
}
