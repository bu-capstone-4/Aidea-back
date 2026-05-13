package com.aidea.aidea.domain.aifeedback.service;

import com.aidea.aidea.domain.aifeedback.entity.Answer;
import com.aidea.aidea.domain.aifeedback.entity.Feedback;
import com.aidea.aidea.domain.aifeedback.entity.FeedbackStatus;
import com.aidea.aidea.domain.aifeedback.entity.Question;
import com.aidea.aidea.domain.aifeedback.repository.FeedbackRepository;
import com.aidea.aidea.domain.aifeedback.service.dto.GeminiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final FeedbackRepository feedbackRepository;
    private final FeedbackEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api-key}") //application.yml에서 키 값 읽어옴
    private String apiKey;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";

    //HTTP 클라이언트
    private final RestClient restClient = RestClient.create();

    @Async
    @Transactional
    public void callGemini(String feedbackId) {  //처음 피드백 요청 받았을 때 호출
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new IllegalStateException("feedback not found: " + feedbackId));

        try {
            String prompt = buildInitialPrompt(
                    feedback.getOriginalMarkdown(),
                    feedback.getAdditionalRequest()
            );

            GeminiResult result = callGeminiApi(prompt);

            // 두 갈래 처리
            if (result.type() == GeminiResult.Type.QUESTIONS) {
                applyQuestioningResult(feedback, result.questions());
            } else {
                applyFeedbackResult(feedback, result.revisedMarkdown());
            }

        } catch (Exception e) {
            log.error("Gemini 호출 실패: feedbackId={}", feedbackId, e);
            applyFailure(feedback);
        }
    }

    @Async
    @Transactional
    public void callGeminiWithAnswers(String feedbackId) {  //사용자가 질문에 답변한 후 호출
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new IllegalStateException("feedback not found"));

        try {
            String prompt = buildAnswerPrompt(
                    feedback.getOriginalMarkdown(),
                    feedback.getQuestions(),
                    feedback.getAnswers(),
                    feedback.getAdditionalRequest()
            );

            GeminiResult result = callGeminiApi(prompt);

            // 답변 후엔 무조건 FEEDBACK이어야 함 (질문이 또 오면 안 됨)
            if (result.type() != GeminiResult.Type.FEEDBACK) {
                throw new IllegalStateException("답변 후엔 FEEDBACK 응답이 와야 함");
            }

            applyFeedbackResult(feedback, result.revisedMarkdown());

        } catch (Exception e) {
            log.error("Gemini 재호출 실패: feedbackId={}", feedbackId, e);
            applyFailure(feedback);
        }
    }

    //질문결과처리
    private void applyQuestioningResult(Feedback feedback, List<Question> questions) {
        feedback.setQuestions(questions);
        feedback.setStatus(FeedbackStatus.QUESTIONING);
        // save() 호출 안 해도 더티 체킹으로 자동 UPDATE — 트랜잭션 끝날 때

        eventPublisher.publishToDocument(
                feedback.getDocument().getId(),
                buildEventJson("feedback:questioning", Map.of(
                        "feedbackId", feedback.getId(),
                        "questions", questions
                ))
        );
    }

    //수정안 결과 처리
    private void applyFeedbackResult(Feedback feedback, String revisedMarkdown) {
        feedback.setRevisedMarkdown(revisedMarkdown);
        feedback.setStatus(FeedbackStatus.DONE);

        eventPublisher.publishToDocument(
                feedback.getDocument().getId(),
                buildEventJson("feedback:ready", Map.of(
                        "feedbackId", feedback.getId(),
                        "revisedMarkdown", revisedMarkdown
                ))
        );
    }

    //실패 처리
    private void applyFailure(Feedback feedback) {
        feedback.setStatus(FeedbackStatus.FAILED);

        eventPublisher.publishToDocument(
                feedback.getDocument().getId(),
                buildEventJson("feedback:error", Map.of(
                        "feedbackId", feedback.getId()
                ))
        );
    }


    //실제 Gemini API 호출
    private GeminiResult callGeminiApi(String prompt) throws Exception {
        // 1. Gemini 요청 body 구성
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", buildResponseSchema()
                )
        );

        // 2. HTTP POST 호출
        Map response = restClient.post()
                .uri(GEMINI_URL + "?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        // 3. 응답 구조 해체: candidates[0].content.parts[0].text
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini가 빈 응답을 반환");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        String resultJson = (String) parts.get(0).get("text");

        // 4. 응답 JSON을 GeminiResult로 역직렬화
        return objectMapper.readValue(resultJson, GeminiResult.class);
    }

    //Gemini가 따라야 할 응답 양식
    private Map<String, Object> buildResponseSchema() {
        // Gemini가 반드시 따라야 할 JSON 스키마
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "type", Map.of(
                                "type", "STRING",
                                "enum", List.of("FEEDBACK", "QUESTIONS")
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
                                                        "items", Map.of("type", "STRING")
                                                )
                                        )
                                )
                        )
                ),
                "required", List.of("type")
        );
    }

    //Gemini에게 줄 첫 명령서 작성
    private String buildInitialPrompt(String originalMarkdown, String additionalRequest) {
        return """
            [ROLE]
            너는 IT 기획 문서 검토 전문 컨설턴트야.

            [CONTEXT]
            사용자가 작성한 기획 문서를 검토해서 둘 중 하나로 응답해야 해:
            - 문서가 충분히 구체적이면 → 수정안을 작성
            - 문서가 너무 빈약하면 → 핵심 정보를 묻는 질문 생성

            [DOCUMENT]
            %s

            [ADDITIONAL REQUEST]
            %s

            [INSTRUCTION]
            1. 문서의 정보량과 구체성을 판단해.
            2. 빈약 판단 기준:
               - 핵심 기능, 타깃 사용자, 차별점 중 2개 이상이 한 줄 미만이거나 없음
               - 또는 전체 문서가 200자 미만
            3. 충분 판단이면 type="FEEDBACK", revisedMarkdown에 개선된 마크다운 작성
               (원본 구조 유지, 부족한 섹션 보강, 모호한 표현 명확화)
            4. 빈약 판단이면 type="QUESTIONS", questions에 핵심 정보를 묻는 질문 3~5개 생성
               각 질문은 id(q1, q2 ...), section(어느 섹션인지), text(질문 내용),
               options(객관식 선택지, 가능하면) 포함

            [OUTPUT]
            반드시 JSON 형식. 다른 형식 절대 안 됨.
            """.formatted(
                originalMarkdown,
                additionalRequest != null ? additionalRequest : "없음"
        );
    }

    //답변 포함된 두 번째 명령서 작성
    private String buildAnswerPrompt(
            String originalMarkdown,
            List<Question> questions,
            List<Answer> answers,
            String additionalRequest
    ) {
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

            [ORIGINAL DOCUMENT]
            %s

            [QUESTIONS AND ANSWERS]
            %s

            [ADDITIONAL REQUEST]
            %s

            [INSTRUCTION]
            - 답변 정보를 원본에 자연스럽게 녹여서 수정안 작성
            - 사용자가 답하지 않은 영역은 추측하지 말고 기존 내용 유지
            - type="FEEDBACK"으로 응답, revisedMarkdown에 마크다운

            [OUTPUT]
            반드시 JSON 형식.
            """.formatted(
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

