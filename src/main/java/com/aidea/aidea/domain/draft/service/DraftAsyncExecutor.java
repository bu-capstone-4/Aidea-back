package com.aidea.aidea.domain.draft.service;

import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentAiStatus;
import com.aidea.aidea.domain.draft.entity.Draft;
import com.aidea.aidea.domain.draft.entity.DraftAnswer;
import com.aidea.aidea.domain.draft.entity.DraftQuestion;
import com.aidea.aidea.domain.draft.entity.DraftStatus;
import com.aidea.aidea.domain.draft.repository.DraftRepository;
import com.aidea.aidea.domain.teamspace.service.TeamspaceEventPublisher;
import com.aidea.aidea.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Component
@RequiredArgsConstructor
@Slf4j
public class DraftAsyncExecutor {

    private final DraftRepository draftRepository;
    private final TeamspaceEventPublisher teamspaceEventPublisher;
    private final DraftEventPublisher draftEventPublisher;
    private final ObjectMapper objectMapper;

    @Lazy
    @Autowired
    private DraftAsyncExecutor self;

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
     * IDEA 문서 초안 생성의 진입점.
     * 짧은 아이디어 설명만으로 바로 초안을 쓰는 대신, 먼저 아이디어를 구체화하기 위한
     * 객관식 질문을 생성해 QUESTIONING으로 전환한다 (질문 → 답변 → 최종 생성의 2단계 흐름).
     * 이 메서드는 IDEA 문서에 대해서만 호출된다 — PLAN 등 나머지 문서는
     * generateDraftAsync(draftId, teamspaceId, teamspaceName, ideaContentOverride)로 단일 호출 생성된다.
     */
    @Async
    @Transactional
    public void generateDraftAsync(String draftId, String teamspaceId, String teamspaceName) {
        log.warn("[DRAFT] generateDraftAsync (IDEA questions) started draftId={} teamspaceId={} thread={}", draftId, teamspaceId, Thread.currentThread().getName());

        Draft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new IllegalStateException("draft not found: " + draftId));
        Document document = draft.getDocument();

        try {
            log.warn("[DRAFT] calling Gemini for IDEA questions draftId={} apiKeyPrefix={}", draftId,
                apiKey != null && apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : "(empty)");
            String prompt = buildIdeaQuestionPrompt(draft.getIdeaContext(), teamspaceName);
            List<DraftQuestion> questions = callGeminiQuestionsApi(prompt);

            draft.setQuestions(questions);
            draft.setStatus(DraftStatus.QUESTIONING);
            log.warn("[DRAFT] QUESTIONING 상태 전환 draftId={} questionCount={}", draftId, questions.size());

            String dId = draft.getId();
            List<DraftQuestion> qs = questions;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishDraftQuestioningSafely(teamspaceId, document.getId(), dId, qs);
                }
            });

        } catch (Exception e) {
            handleDraftFailure(draft, document, teamspaceId, e);
        }
    }

    /**
     * IDEA 질문에 대한 답변(또는 빈 답변 — 건너뛰기) 제출 후 호출되는 최종 생성 단계.
     * 원본 아이디어 설명에 Q&A로 확보한 정보를 더해 최종 IDEA 초안을 생성하고,
     * 완료되면 기존과 동일하게 draft:ready/draft:applied를 발행하고 나머지 문서 초안을 트리거한다.
     */
    @Async
    @Transactional
    public void generateFinalIdeaDraft(String draftId, String teamspaceId, String teamspaceName) {
        log.warn("[DRAFT] generateFinalIdeaDraft started draftId={} teamspaceId={} thread={}", draftId, teamspaceId, Thread.currentThread().getName());

        Draft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new IllegalStateException("draft not found: " + draftId));
        Document document = draft.getDocument();

        try {
            String enrichedContext = mergeIdeaContextWithAnswers(draft.getIdeaContext(), draft.getQuestions(), draft.getAnswers());
            String prompt = buildPrompt(document.getType(), enrichedContext, teamspaceName);
            String content = callGeminiApi(prompt);

            draft.setContent(content);
            draft.setStatus(DraftStatus.DONE);
            document.setStatus(DocumentAiStatus.IDLE);

            publishDraftReadySafely(teamspaceId, document.getId(), draft.getId(), content);
            publishDraftAppliedToDocument(document.getId(), draft.getId(), content);
            log.warn("[DRAFT] IDEA final generation complete draftId={}", draftId);

            final String ideaContent = content;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.warn("[DRAFT] IDEA done - triggering pending drafts teamspaceId={}", teamspaceId);
                    triggerPendingDraftsWithIdeaContent(teamspaceId, teamspaceName, ideaContent);
                }
            });

        } catch (Exception e) {
            handleDraftFailure(draft, document, teamspaceId, e);
        }
    }

    private void triggerPendingDraftsWithIdeaContent(String teamspaceId, String teamspaceName, String ideaContent) {
        List<Draft> pendingDrafts = draftRepository.findByTeamspaceIdAndStatus(teamspaceId, DraftStatus.PENDING);
        log.warn("[DRAFT] triggering {} pending drafts after IDEA completion teamspaceId={}", pendingDrafts.size(), teamspaceId);
        for (Draft pending : pendingDrafts) {
            self.generateDraftAsync(pending.getId(), teamspaceId, teamspaceName, ideaContent);
        }
    }

    @Async
    @Transactional
    public void generateDraftAsync(String draftId, String teamspaceId, String teamspaceName, String ideaContentOverride) {
        log.warn("[DRAFT] generateDraftAsync (pending) started draftId={}", draftId);

        Draft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new IllegalStateException("draft not found: " + draftId));
        Document document = draft.getDocument();

        document.setStatus(DocumentAiStatus.DRAFT);

        try {
            String contextToUse = (ideaContentOverride != null) ? ideaContentOverride : draft.getIdeaContext();
            String prompt = buildPrompt(document.getType(), contextToUse, teamspaceName);
            String content = callGeminiApi(prompt);

            draft.setContent(content);
            draft.setStatus(DraftStatus.DONE);
            document.setStatus(DocumentAiStatus.IDLE);

            publishDraftReadySafely(teamspaceId, document.getId(), draft.getId(), content);
            publishDraftAppliedToDocument(document.getId(), draft.getId(), content);
            log.warn("[DRAFT] pending draft complete draftId={}", draftId);

        } catch (Exception e) {
            handleDraftFailure(draft, document, teamspaceId, e);
        }
    }

    private void handleDraftFailure(Draft draft, Document document, String teamspaceId, Exception e) {
        ErrorCode errorCode = classifyException(e);
        log.error("[DRAFT] generation failed draftId={} errorCode={}", draft.getId(), errorCode.getCode(), e);

        String errorMsg = e.getMessage();
        if (errorMsg != null && errorMsg.length() > 500) {
            errorMsg = errorMsg.substring(0, 500);
        }
        draft.setErrorMessage(errorMsg);
        draft.setErrorCode(errorCode.getCode());
        draft.setStatus(DraftStatus.FAILED);
        document.setStatus(DocumentAiStatus.IDLE);

        log.warn("[DRAFT] publishing draft:error draftId={} teamspaceId={} errorCode={}", draft.getId(), teamspaceId, errorCode.getCode());
        publishDraftErrorSafely(teamspaceId, document.getId(), errorCode.getCode());
        publishDraftErrorToDocument(document.getId(), errorCode.getCode());
    }

    private void publishDraftQuestioningSafely(String teamspaceId, String documentId, String draftId, List<DraftQuestion> questions) {
        try {
            teamspaceEventPublisher.publishDraftQuestioning(teamspaceId, documentId, draftId, questions);
        } catch (Exception e) {
            log.error("[DRAFT] failed to publish draft:questioning teamspaceId={} documentId={}", teamspaceId, documentId, e);
        }
    }

    private void publishDraftReadySafely(String teamspaceId, String documentId, String draftId, String content) {
        try {
            teamspaceEventPublisher.publishDraftReady(teamspaceId, documentId, draftId, content);
        } catch (Exception e) {
            log.error("[DRAFT] failed to publish draft:ready teamspaceId={} documentId={}", teamspaceId, documentId, e);
        }
    }

    private void publishDraftErrorSafely(String teamspaceId, String documentId, String errorCode) {
        try {
            teamspaceEventPublisher.publishDraftError(teamspaceId, documentId, errorCode);
        } catch (Exception e) {
            log.error("[DRAFT] failed to publish draft:error teamspaceId={} documentId={}", teamspaceId, documentId, e);
        }
    }

    private ErrorCode classifyException(Exception e) {
        if (e instanceof HttpClientErrorException httpEx) {
            if (httpEx.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                return ErrorCode.DRAFT_QUOTA_EXCEEDED;
            }
            return ErrorCode.DRAFT_GEMINI_API_ERROR;
        }
        if (e instanceof HttpServerErrorException || e instanceof RestClientException) {
            return ErrorCode.DRAFT_GEMINI_API_ERROR;
        }
        if (e instanceof GeminiInvalidResponseException) {
            return ErrorCode.DRAFT_GEMINI_INVALID_RESPONSE;
        }
        return ErrorCode.DRAFT_GENERATION_FAILED;
    }

    private void publishDraftErrorToDocument(String documentId, String errorCode) {
        try {
            Map<String, Object> event = new java.util.LinkedHashMap<>();
            event.put("type", "draft:error");
            event.put("errorCode", errorCode);
            draftEventPublisher.publishDraftToDocument(documentId, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("[DRAFT] failed to publish draft:error to document WS documentId={}", documentId, e);
        }
    }

    static class GeminiInvalidResponseException extends RuntimeException {
        GeminiInvalidResponseException(String message) {
            super(message);
        }
    }

    private String stripMarkdownFence(String text) {
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.lastIndexOf("```")).stripTrailing();
            }
        }
        return trimmed;
    }

    private void publishDraftAppliedToDocument(String documentId, String draftId, String content) {
        try {
            Map<String, Object> event = new java.util.LinkedHashMap<>();
            event.put("type", "draft:applied");
            event.put("draftId", draftId);
            event.put("content", content);
            draftEventPublisher.publishDraftToDocument(documentId, objectMapper.writeValueAsString(event));
            log.warn("[DRAFT] published draft:applied docId={} draftId={}", documentId, draftId);
        } catch (Exception e) {
            log.error("[DRAFT] failed to publish draft:applied docId={}", documentId, e);
        }
    }

    private String buildPrompt(com.aidea.aidea.domain.documents.entity.DocumentType type,
                                String ideaContext, String teamspaceName) {
        String typeInstruction = type.displayName() + " (" + type.requiredElements() + " 포함)";

        String projectSection = (teamspaceName != null && !teamspaceName.isBlank())
                ? "[PROJECT NAME]\n" + teamspaceName + "\n\n"
                : "";

        String ideaSection = (ideaContext != null && !ideaContext.isBlank())
                ? "[IDEA]\n" + ideaContext + "\n\n"
                : "";

        return """
            [ROLE] 너는 IT 스타트업 기획 전문가야.
            [TASK] 새 팀 프로젝트의 %s 초안을 마크다운으로 작성해줘.
            %s%s[INSTRUCTION]
            - 실제로 채워야 할 내용은 [TODO: 구체적으로 무엇을 써야 하는지] 형태로 표시
            - 이미 알 수 있는 내용(문서 제목, 기본 구조, 아이디어에서 유추 가능한 내용)은 직접 작성
            - 위에서 언급한 핵심 항목을 각각 별도 섹션으로 구성하고, 섹션 제목은 구체적으로 작성
            - 각 섹션은 200~400자 내외로 작성 (TODO로 표시만 하는 부분은 분량 제한에서 제외)
            """.formatted(typeInstruction, projectSection, ideaSection);
    }

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 3_000L;

    private String callGeminiApi(String prompt) throws Exception {
        return callWithRetry(() -> executeGeminiRequest(prompt));
    }

    private List<DraftQuestion> callGeminiQuestionsApi(String prompt) throws Exception {
        return callWithRetry(() -> executeIdeaQuestionsRequest(prompt));
    }

    private <T> T callWithRetry(Callable<T> call) throws Exception {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return call.call();
            } catch (HttpClientErrorException e) {
                throw e; // 4xx는 재시도 불가
            } catch (Exception e) {
                if (attempt < MAX_RETRIES && isRetryableException(e)) {
                    log.warn("[DRAFT] Gemini 재시도 ({}/{}) {}ms 후... 오류: {}", attempt + 1, MAX_RETRIES, RETRY_DELAY_MS, e.getMessage());
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
        return e instanceof RestClientException; // 연결 오류, 타임아웃 등
    }

    private String executeGeminiRequest(String prompt) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
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

        String raw = parts.stream()
                .filter(p -> !Boolean.TRUE.equals(p.get("thought")))
                .map(p -> (String) p.get("text"))
                .filter(t -> t != null && !t.isBlank())
                .findFirst()
                .orElseThrow(() -> new GeminiInvalidResponseException("Gemini text 파트 없음"));

        return stripMarkdownFence(raw);
    }

    // IDEA 초안을 구체화하기 위한 질문 생성 프롬프트 (2단계의 "항상 객관식" 패턴 재사용)
    private String buildIdeaQuestionPrompt(String ideaContext, String teamspaceName) {
        String projectSection = (teamspaceName != null && !teamspaceName.isBlank())
                ? "[PROJECT NAME]\n" + teamspaceName + "\n\n"
                : "";

        return """
            [ROLE] 너는 IT 스타트업 기획 컨설턴트야.
            [CONTEXT]
            사용자가 새 팀 프로젝트의 아이디어를 짧게 설명했어.
            이 설명만으로 바로 기획서 초안을 쓰기엔 정보가 부족할 수 있어서,
            본격적으로 작성하기 전에 더 구체적인 정보를 끌어내는 질문을 먼저 만들려고 해.

            %s[IDEA]
            %s

            [INSTRUCTION]
            1. 이 아이디어를 구체화하는 데 꼭 필요한 핵심 정보(%s)를 중심으로 질문 3~5개를 만들어.
            2. 각 질문은 id(q1, q2 ...), section(어떤 항목에 대한 질문인지), text(질문 내용)와 함께
               options에 서로 구분되는 구체적인 보기를 3~4개 반드시 포함해.
            3. 자유 서술형(주관식) 질문은 만들지 마라 — 사용자가 그 중 하나를 고르거나
               직접 입력할 수 있도록, 보기는 실제로 선택 가능한 구체적인 값으로 작성해.

            [OUTPUT]
            반드시 JSON 형식. 다른 형식 절대 안 됨.
            """.formatted(
                    projectSection,
                    ideaContext,
                    com.aidea.aidea.domain.documents.entity.DocumentType.IDEA.requiredElements()
            );
    }

    // 2단계에서 확립한 "options 필수 + minItems/maxItems" 스키마 패턴을 그대로 재사용
    private Map<String, Object> buildIdeaQuestionResponseSchema() {
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "questions", Map.of(
                                "type", "ARRAY",
                                "items", Map.of(
                                        "type", "OBJECT",
                                        "properties", Map.of(
                                                "id", Map.of("type", "STRING"),
                                                "section", Map.of("type", "STRING"),
                                                "text", Map.of("type", "STRING"),
                                                "options", Map.of(
                                                        "type", "ARRAY",
                                                        "items", Map.of("type", "STRING"),
                                                        "minItems", 3,
                                                        "maxItems", 5
                                                )
                                        ),
                                        "required", List.of("id", "section", "text", "options")
                                ),
                                "minItems", 3,
                                "maxItems", 5
                        )
                ),
                "required", List.of("questions")
        );
    }

    private record IdeaQuestionsResult(List<DraftQuestion> questions) {}

    private List<DraftQuestion> executeIdeaQuestionsRequest(String prompt) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", buildIdeaQuestionResponseSchema(),
                        "thinkingConfig", Map.of("thinkingBudget", 1024),
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

        IdeaQuestionsResult result = objectMapper.readValue(resultJson, IdeaQuestionsResult.class);
        if (result.questions() == null || result.questions().isEmpty()) {
            throw new GeminiInvalidResponseException("Gemini가 질문을 생성하지 않음");
        }
        return result.questions();
    }

    // 사용자가 답변을 제출했다면 원본 아이디어 설명에 Q&A 내용을 더해 최종 생성 프롬프트의 컨텍스트로 사용
    // (답변을 건너뛴 경우 원본 설명만으로 바로 진행 — 기존 buildPrompt를 그대로 재사용하기 위해
    //  Q&A를 별도 인자로 받지 않고 [IDEA] 컨텍스트 문자열에 합쳐 넣는다)
    private String mergeIdeaContextWithAnswers(String ideaContext, List<DraftQuestion> questions, List<DraftAnswer> answers) {
        if (questions == null || answers == null || answers.isEmpty()) {
            return ideaContext;
        }

        StringBuilder qa = new StringBuilder();
        for (DraftQuestion q : questions) {
            answers.stream()
                    .filter(a -> a.questionId().equals(q.id()))
                    .map(DraftAnswer::value)
                    .filter(v -> v != null && !v.isBlank())
                    .findFirst()
                    .ifPresent(value -> qa.append("- ").append(q.text()).append(" → ").append(value).append("\n"));
        }

        if (qa.isEmpty()) {
            return ideaContext;
        }
        return ideaContext + "\n\n[추가로 확인된 정보]\n" + qa;
    }
}
