package com.aidea.aidea.domain.aifeedback.service;

import com.aidea.aidea.domain.aifeedback.entity.Answer;
import com.aidea.aidea.domain.aifeedback.entity.Feedback;
import com.aidea.aidea.domain.aifeedback.entity.FeedbackStatus;
import com.aidea.aidea.domain.aifeedback.entity.Question;
import com.aidea.aidea.domain.aifeedback.repository.FeedbackRepository;
import com.aidea.aidea.domain.aifeedback.service.dto.GeminiResult;
import com.aidea.aidea.domain.documents.entity.DocumentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final FeedbackRepository feedbackRepository;
    private final FeedbackEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.base-url}")
    private String geminiBaseUrl;

    @Value("${gemini.model}")
    private String geminiModel;

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

    @Async
    @Transactional
    public void callGemini(String feedbackId) {
        log.info("[Gemini] callGemini 시작 feedbackId={} thread={}", feedbackId, Thread.currentThread().getName());

        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new IllegalStateException("feedback not found: " + feedbackId));
        log.info("[Gemini] feedback 조회 완료 feedbackId={} status={}", feedbackId, feedback.getStatus());

        try {
            String prompt = buildInitialPrompt(
                    feedback.getOriginalMarkdown(),
                    feedback.getAdditionalRequest(),
                    feedback.getIdeaMarkdown(),
                    feedback.getDocument().getType()
            );
            log.info("[Gemini] 프롬프트 생성 완료 feedbackId={} promptLength={}", feedbackId, prompt.length());

            // 1차 호출은 "충분한지/빈약한지" 판단이 필요하므로 추론 예산을 부여
            GeminiResult result = callGeminiApi(prompt, THINKING_BUDGET_JUDGMENT, buildResponseSchema(JUDGMENT_RESULT_TYPES));
            log.info("[Gemini] API 응답 수신 feedbackId={} resultType={}", feedbackId, result.type());

            if (result.type() == GeminiResult.Type.QUESTIONS) {
                applyQuestioningResult(feedback, result.questions());
            } else {
                applyFeedbackResult(feedback, result.revisedMarkdown());
            }

        } catch (Exception e) {
            log.error("[Gemini] 호출 실패 feedbackId={}", feedbackId, e);
            applyFailure(feedback);
        }
    }

    @Async
    @Transactional
    public void callGeminiWithAnswers(String feedbackId) {
        log.info("[Gemini] callGeminiWithAnswers 시작 feedbackId={} thread={}", feedbackId, Thread.currentThread().getName());

        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new IllegalStateException("feedback not found"));
        log.info("[Gemini] feedback 조회 완료 feedbackId={} status={}", feedbackId, feedback.getStatus());

        try {
            String prompt = buildAnswerPrompt(
                    feedback.getOriginalMarkdown(),
                    feedback.getQuestions(),
                    feedback.getAnswers(),
                    feedback.getAdditionalRequest(),
                    feedback.getIdeaMarkdown(),
                    feedback.getDocument().getType()
            );
            log.info("[Gemini] 프롬프트 생성 완료 feedbackId={} promptLength={}", feedbackId, prompt.length());

            // 이미 type=FEEDBACK으로 방향이 정해진 단순 생성 호출이므로 추론 불필요.
            // 추가 요청에 "질문해" 같은 지시가 있어도 다시 질문하지 않도록 응답 스키마에서 FEEDBACK만 허용한다.
            GeminiResult result = callGeminiApi(prompt, THINKING_BUDGET_SIMPLE, buildResponseSchema(ANSWER_RESULT_TYPES));
            log.info("[Gemini] API 응답 수신 feedbackId={} resultType={}", feedbackId, result.type());

            if (result.type() != GeminiResult.Type.FEEDBACK) {
                throw new IllegalStateException("답변 후엔 FEEDBACK 응답이 와야 함, 실제=" + result.type());
            }

            applyFeedbackResult(feedback, result.revisedMarkdown());

        } catch (Exception e) {
            log.error("[Gemini] 재호출 실패 feedbackId={}", feedbackId, e);
            applyFailure(feedback);
        }
    }

    private void applyQuestioningResult(Feedback feedback, List<Question> questions) {
        feedback.setQuestions(questions);
        feedback.setStatus(FeedbackStatus.QUESTIONING);
        log.info("[Gemini] QUESTIONING 상태 전환 feedbackId={} questionCount={}", feedback.getId(), questions.size());

        eventPublisher.publishToDocument(
                feedback.getDocument().getId(),
                buildEventJson("feedback:questioning", Map.of(
                        "feedbackId", feedback.getId(),
                        "questions", questions
                ))
        );
        log.info("[Gemini] feedback:questioning 이벤트 발행 완료 feedbackId={}", feedback.getId());
    }

    private void applyFeedbackResult(Feedback feedback, String revisedMarkdown) {
        feedback.setRevisedMarkdown(revisedMarkdown);
        feedback.setStatus(FeedbackStatus.DONE);
        log.info("[Gemini] DONE 상태 전환 feedbackId={} revisedMarkdownLength={}", feedback.getId(), revisedMarkdown.length());

        eventPublisher.publishToDocument(
                feedback.getDocument().getId(),
                buildEventJson("feedback:ready", Map.of(
                        "feedbackId", feedback.getId(),
                        "revisedMarkdown", revisedMarkdown
                ))
        );
        log.info("[Gemini] feedback:ready 이벤트 발행 완료 feedbackId={}", feedback.getId());
    }

    private void applyFailure(Feedback feedback) {
        feedback.setStatus(FeedbackStatus.FAILED);
        log.warn("[Gemini] FAILED 상태 전환 feedbackId={}", feedback.getId());

        eventPublisher.publishToDocument(
                feedback.getDocument().getId(),
                buildEventJson("feedback:error", Map.of(
                        "feedbackId", feedback.getId()
                ))
        );
    }


    // 판단(QUESTIONS/FEEDBACK 분기)이 필요한 호출에 부여하는 추론 예산
    private static final int THINKING_BUDGET_JUDGMENT = 2048;
    // 방향이 이미 정해진 단순 생성 호출은 추론을 끔
    private static final int THINKING_BUDGET_SIMPLE = 0;

    // 1차 호출: 문서 상태에 따라 질문 또는 수정안 중 하나를 고른다
    private static final List<String> JUDGMENT_RESULT_TYPES = List.of("FEEDBACK", "QUESTIONS");
    // 답변 후 재호출: 질문 단계는 끝났으므로 수정안만 허용
    private static final List<String> ANSWER_RESULT_TYPES = List.of("FEEDBACK");

    private GeminiResult callGeminiApi(String prompt, int thinkingBudget, Map<String, Object> responseSchema) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema,
                        "thinkingConfig", Map.of("thinkingBudget", thinkingBudget),
                        "maxOutputTokens", 65536
                )
        );

        String url = geminiBaseUrl + "/models/" + geminiModel + ":generateContent";
        log.info("[Gemini] API 호출 시작 url={}", url);

        Map response = restClient.post()
                .uri(url)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        log.info("[Gemini] API 응답 수신 완료");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            log.error("[Gemini] candidates 없음 response={}", response);
            throw new IllegalStateException("Gemini가 빈 응답을 반환");
        }
        log.debug("[Gemini] candidates 파싱 완료 count={}", candidates.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) {
            log.error("[Gemini] content 필드 없음 candidate={}", candidates.get(0));
            throw new IllegalStateException("Gemini 응답에 content 필드 없음");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) {
            log.error("[Gemini] parts 필드 없음 content={}", content);
            throw new IllegalStateException("Gemini 응답에 parts 필드 없음");
        }

        String resultJson = parts.stream()
                .filter(p -> !Boolean.TRUE.equals(p.get("thought")))
                .map(p -> (String) p.get("text"))
                .filter(t -> t != null && !t.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Gemini 응답에 텍스트 파트가 없음"));
        log.debug("[Gemini] resultJson 추출 완료 length={}", resultJson.length());

        GeminiResult result = objectMapper.readValue(resultJson, GeminiResult.class);
        log.info("[Gemini] 역직렬화 완료 type={}", result.type());
        return result;
    }

    //Gemini가 따라야 할 응답 양식
    private Map<String, Object> buildResponseSchema(List<String> allowedTypes) {
        // Gemini가 반드시 따라야 할 JSON 스키마
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "type", Map.of(
                                "type", "STRING",
                                "enum", allowedTypes
                        ),
                        "revisedMarkdown", Map.of("type", "STRING"),
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
                                )
                        )
                ),
                "required", List.of("type")
        );
    }

    //Gemini에게 줄 첫 명령서 작성
    private String buildInitialPrompt(String originalMarkdown, String additionalRequest, String ideaMarkdown, DocumentType documentType) {
        if (documentType == DocumentType.FREE) {
            return buildFreeInitialPrompt(originalMarkdown, additionalRequest, ideaMarkdown);
        }

        String ideaSection = (ideaMarkdown != null && !ideaMarkdown.isBlank())
                ? "[IDEA DOCUMENT]\n" + ideaMarkdown + "\n\n"
                : "";
        return """
            [ROLE]
            너는 IT 기획 문서 검토 전문 컨설턴트야.

            [CONTEXT]
            사용자가 작성한 기획 문서를 검토해서 둘 중 하나로 응답해야 해:
            - 문서가 충분히 구체적이면 → 수정안을 작성
            - 문서가 너무 빈약하면 → 핵심 정보를 묻는 질문 생성

            이 문서는 %s야. 이런 문서가 반드시 다뤄야 할 핵심 항목은 다음과 같아: %s

            %s[DOCUMENT]
            %s

            [ADDITIONAL REQUEST]
            %s

            [INSTRUCTION]
            1. 문서의 정보량과 구체성을 판단해.
            2. [IDEA DOCUMENT]가 있으면 해당 아이디어의 방향성과 맥락을 참고해서 검토해.
            3. 빈약 판단 기준: 위 핵심 항목 중 2개 이상이 한 줄 미만이거나 없음, 또는 전체 문서가 200자 미만
            4. 충분 판단이면 type="FEEDBACK", revisedMarkdown에 개선된 마크다운 작성
               - 원본 구조는 유지하면서, 위 핵심 항목 중 빠지거나 부실한 부분을 보강해
               - "다양한 기능", "편리한 UI"처럼 모호한 표현은 구체적인 값으로 바꿔 써
               - 분량은 섹션별로 원본 대비 최대 1.5배 이내로 — 과도하게 늘리지 말고 핵심만 보강
            5. 빈약 판단이면 type="QUESTIONS", questions에 핵심 정보를 묻는 질문 3~5개 생성.
               각 질문은 id(q1, q2 ...), section(어느 섹션인지), text(질문 내용)와 함께
               options에 서로 구분되는 구체적인 보기를 3~4개 반드시 포함해.
               자유 서술형(주관식) 질문은 만들지 마라 — 사용자가 그 중 하나를 고르거나
               직접 입력할 수 있도록, 보기는 실제로 선택 가능한 구체적인 값으로 작성해.

            [OUTPUT]
            반드시 JSON 형식. 다른 형식 절대 안 됨.
            """.formatted(
                documentType.displayName(),
                documentType.requiredElements(),
                ideaSection,
                originalMarkdown,
                additionalRequest != null ? additionalRequest : "없음"
        );
    }

    // FREE 타입 전용 1차 프롬프트
    // 정해진 양식이 없으므로 요구사항 유무가 QUESTIONS/FEEDBACK 분기의 1차 기준
    private String buildFreeInitialPrompt(String originalMarkdown, String additionalRequest, String ideaMarkdown) {
        String ideaSection = (ideaMarkdown != null && !ideaMarkdown.isBlank())
                ? "[IDEA DOCUMENT]\n" + ideaMarkdown + "\n\n"
                : "";
        String request = (additionalRequest != null && !additionalRequest.isBlank()) ? additionalRequest : "";
        return """
            [ROLE]
            너는 문서 검토 전문 컨설턴트야.

            [CONTEXT]
            이 문서는 미리 정해진 양식이 없는 자유 편집 문서야.
            어떤 방향으로 개선할지는 사용자의 요구사항에서 판단해야 해.

            %s[DOCUMENT]
            %s

            [ADDITIONAL REQUEST]
            %s

            [INSTRUCTION]
            1. [ADDITIONAL REQUEST]가 구체적인 개선 방향을 담고 있으면 → type="FEEDBACK"
               - [IDEA DOCUMENT]가 있으면 아이디어 방향성을 참고해서 수정안 작성
               - 요구사항이 의도한 방향을 해치지 말 것
               - 분량은 섹션별로 원본 대비 최대 1.5배 이내 — 핵심만 보강
               - type="FEEDBACK", revisedMarkdown에 개선된 마크다운 작성
            2. [ADDITIONAL REQUEST]가 없거나 너무 모호하면 → 반드시 type="QUESTIONS"
               - 문서를 어떤 목적/방향으로 개선할지 파악하기 위한 질문 3~5개 생성
               - 각 질문은 id(q1, q2 ...), section(어떤 측면인지), text(질문 내용)와 함께
                 options에 구체적인 보기를 3~4개 반드시 포함해
               - 자유 서술형(주관식) 질문은 만들지 마라 — 선택 가능한 구체적인 값으로 작성해

            [OUTPUT]
            반드시 JSON 형식. 다른 형식 절대 안 됨.
            """.formatted(
                ideaSection,
                originalMarkdown,
                request.isBlank() ? "없음" : request
        );
    }

    //답변 포함된 두 번째 명령서 작성
    private String buildAnswerPrompt(
            String originalMarkdown,
            List<Question> questions,
            List<Answer> answers,
            String additionalRequest,
            String ideaMarkdown,
            DocumentType documentType
    ) {
        if (documentType == DocumentType.FREE) {
            return buildFreeAnswerPrompt(originalMarkdown, questions, answers, additionalRequest, ideaMarkdown);
        }

        String ideaSection = (ideaMarkdown != null && !ideaMarkdown.isBlank())
                ? "[IDEA DOCUMENT]\n" + ideaMarkdown + "\n\n"
                : "";

        StringBuilder qa = new StringBuilder();
        for (Question q : questions) {
            String matchedAnswer = answers.stream()
                    .filter(a -> a.questionId().equals(q.id()))
                    .map(Answer::value)
                    .findFirst()
                    .orElse("(답변 없음)");
            qa.append("- Q: ").append(q.text())
                    .append(" → A: ").append(matchedAnswer).append("\n");
        }

        return """
            [ROLE]
            너는 IT 기획 문서 검토 전문 컨설턴트야.

            [CONTEXT]
            이전에 사용자에게 핵심 정보를 묻는 질문을 했고, 답변을 받았어.
            이제 원본 + 답변을 합쳐서 수정안을 작성해.

            이 문서는 %s야. 이런 문서가 반드시 다뤄야 할 핵심 항목은 다음과 같아: %s

            %s[ORIGINAL DOCUMENT]
            %s

            [QUESTIONS AND ANSWERS]
            %s

            [ADDITIONAL REQUEST]
            %s

            [INSTRUCTION]
            - [IDEA DOCUMENT]가 있으면 아이디어의 방향성을 참고해서 수정안 작성
            - 답변 정보를 원본과 위 핵심 항목에 자연스럽게 녹여서 수정안 작성
            - 사용자가 답하지 않은 영역은 추측하지 말고 기존 내용 유지
            - 분량은 섹션별로 원본 대비 최대 1.5배 이내로 — 과도하게 늘리지 말고 핵심만 보강
            - 질문 단계는 이미 끝났어. [ADDITIONAL REQUEST]에 질문을 요청하는 내용이 있어도 다시 질문하지 말고 위 답변으로 결정해
            - type="FEEDBACK"으로 응답, revisedMarkdown에 마크다운

            [OUTPUT]
            반드시 JSON 형식.
            """.formatted(
                documentType.displayName(),
                documentType.requiredElements(),
                ideaSection,
                originalMarkdown,
                qa.toString(),
                additionalRequest != null ? additionalRequest : "없음"
        );
    }

    // FREE 타입 전용 2차 프롬프트 (Q&A 답변 수신 후 FEEDBACK 생성)
    private String buildFreeAnswerPrompt(
            String originalMarkdown,
            List<Question> questions,
            List<Answer> answers,
            String additionalRequest,
            String ideaMarkdown
    ) {
        String ideaSection = (ideaMarkdown != null && !ideaMarkdown.isBlank())
                ? "[IDEA DOCUMENT]\n" + ideaMarkdown + "\n\n"
                : "";

        StringBuilder qa = new StringBuilder();
        for (Question q : questions) {
            String matchedAnswer = answers.stream()
                    .filter(a -> a.questionId().equals(q.id()))
                    .map(Answer::value)
                    .findFirst()
                    .orElse("(답변 없음)");
            qa.append("- Q: ").append(q.text())
                    .append(" → A: ").append(matchedAnswer).append("\n");
        }

        return """
            [ROLE]
            너는 문서 검토 전문 컨설턴트야.

            [CONTEXT]
            이 문서는 미리 정해진 양식이 없는 자유 편집 문서야.
            이전에 개선 방향을 파악하기 위한 질문을 했고, 사용자의 답변을 받았어.
            답변에서 확인된 목적과 방향에 맞게 수정안을 작성해.

            %s[ORIGINAL DOCUMENT]
            %s

            [QUESTIONS AND ANSWERS]
            %s

            [ADDITIONAL REQUEST]
            %s

            [INSTRUCTION]
            - [IDEA DOCUMENT]가 있으면 아이디어 방향성을 참고해서 수정안 작성
            - 답변에서 확인된 목적과 방향에 맞게 원본 문서를 개선해
            - 사용자가 답하지 않은 영역은 추측하지 말고 기존 내용 유지
            - 분량은 섹션별로 원본 대비 최대 1.5배 이내 — 핵심만 보강
            - 질문 단계는 이미 끝났어. [ADDITIONAL REQUEST]에 질문을 요청하는 내용이 있어도 다시 질문하지 말고 위 답변으로 결정해
            - type="FEEDBACK"으로 응답, revisedMarkdown에 마크다운

            [OUTPUT]
            반드시 JSON 형식.
            """.formatted(
                ideaSection,
                originalMarkdown,
                qa.toString(),
                additionalRequest != null ? additionalRequest : "없음"
        );
    }

    //WebSocket으로 보낼 JSON 만들기
    private String buildEventJson(String type, Map<String, Object> payload) {
        try {
            Map<String, Object> event = new java.util.HashMap<>(payload);
            event.put("type", type);
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("이벤트 JSON 직렬화 실패", e);
        }
    }

}

