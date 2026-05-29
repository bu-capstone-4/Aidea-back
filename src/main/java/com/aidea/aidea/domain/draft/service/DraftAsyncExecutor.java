package com.aidea.aidea.domain.draft.service;

import com.aidea.aidea.domain.aifeedback.service.FeedbackEventPublisher;
import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentAiStatus;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.draft.entity.Draft;
import com.aidea.aidea.domain.draft.entity.DraftStatus;
import com.aidea.aidea.domain.draft.repository.DraftRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DraftAsyncExecutor {

    private final DraftRepository draftRepository;
    private final DocumentRepository documentRepository;
    private final FeedbackEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api-key}")  private String apiKey;
    @Value("${gemini.base-url}") private String geminiBaseUrl;
    @Value("${gemini.model}")    private String geminiModel;

    private final RestClient restClient = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .build()))
            .build();

    @Async
    @Transactional
    public void generateDraftAsync(String draftId) {
        log.info("[DRAFT] 생성 시작 draftId={} thread={}", draftId, Thread.currentThread().getName());

        Draft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new IllegalStateException("draft not found: " + draftId));
        Document document = draft.getDocument();

        try {
            String prompt = buildPrompt(document.getType());
            String content = callGeminiApi(prompt);

            draft.setContent(content);
            draft.setStatus(DraftStatus.DONE);
            document.setStatus(DocumentAiStatus.IDLE);

            publishEvent(document.getId(), "draft:ready", Map.of("content", content));
            log.info("[DRAFT] 생성 완료 draftId={}", draftId);

        } catch (Exception e) {
            log.error("[DRAFT] 생성 실패 draftId={}", draftId, e);

            draft.setErrorMessage("초안 생성에 실패했습니다.");
            draft.setStatus(DraftStatus.FAILED);
            document.setStatus(DocumentAiStatus.IDLE);

            publishEvent(document.getId(), "draft:error",
                    Map.of("errorMessage", "초안 생성에 실패했습니다."));
        }
    }

    private String buildPrompt(com.aidea.aidea.domain.documents.entity.DocumentType type) {
        String typeInstruction = switch (type) {
            case IDEA          -> "서비스 아이디어 기획서 (핵심 가치, 타깃 사용자, 차별점 포함)";
            case PLAN          -> "프로젝트 계획서 (목표, 주요 기능, 개발 단계, 예상 일정 포함)";
            case USER_SCENARIO -> "유저 시나리오 문서 (주요 사용자 유형, Use Case 3~5개 포함)";
            case API_SPEC      -> "REST API 명세서 (주요 엔드포인트, 요청/응답 형식 포함)";
            case ERD           -> "ERD 설명 문서 (주요 엔티티, 핵심 필드, 관계 포함)";
        };

        return """
            [ROLE] 너는 IT 스타트업 기획 전문가야.
            [TASK] 새 팀 프로젝트의 %s 초안을 마크다운으로 작성해줘.
            [INSTRUCTION]
            - 실제로 채워야 할 내용은 [TODO] 형태로 표시
            - 섹션 제목은 구체적으로 작성
            - 전체 길이 1000자
            """.formatted(typeInstruction);
    }

    private String callGeminiApi(String prompt) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of("content", Map.of("type", "STRING")),
                                "required", List.of("content")
                        ),
                        "maxOutputTokens", 4096
                )
        );

        String url = geminiBaseUrl + "/models/" + geminiModel + ":generateContent?key=" + apiKey;

        Map response = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        String json = (String) parts.get(0).get("text");

        Map<String, String> result = objectMapper.readValue(json, Map.class);
        return result.get("content");
    }

    private void publishEvent(String documentId, String type, Map<String, Object> payload) {
        try {
            Map<String, Object> event = new HashMap<>(payload);
            event.put("type", type);
            event.put("documentId", documentId);
            eventPublisher.publishToDocument(documentId, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("[DRAFT] 이벤트 발행 실패 type={}", type, e);
        }
    }
}