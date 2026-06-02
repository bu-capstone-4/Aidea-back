package com.aidea.aidea.domain.draft.service;

import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.entity.DocumentAiStatus;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.draft.entity.Draft;
import com.aidea.aidea.domain.draft.entity.DraftStatus;
import com.aidea.aidea.domain.draft.repository.DraftRepository;
import com.aidea.aidea.domain.teamspace.service.TeamspaceEventPublisher;
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
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DraftAsyncExecutor {

    private final DraftRepository draftRepository;
    private final DocumentRepository documentRepository;
    private final TeamspaceEventPublisher teamspaceEventPublisher;
    private final DraftEventPublisher draftEventPublisher;
    private final ObjectMapper objectMapper;

    @Lazy
    @Autowired
    private DraftAsyncExecutor self;

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
    public void generateDraftAsync(String draftId, String teamspaceId, String teamspaceName) {
        log.warn("[DRAFT] generateDraftAsync started draftId={} teamspaceId={} thread={}", draftId, teamspaceId, Thread.currentThread().getName());

        Draft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new IllegalStateException("draft not found: " + draftId));
        Document document = draft.getDocument();

        try {
            log.warn("[DRAFT] calling Gemini draftId={} docType={}", draftId, document.getType());
            String prompt = buildPrompt(document.getType(), draft.getIdeaContext(), teamspaceName);
            String content = callGeminiApi(prompt);

            draft.setContent(content);
            draft.setStatus(DraftStatus.DONE);
            document.setStatus(DocumentAiStatus.IDLE);

            log.warn("[DRAFT] publishing draft:ready draftId={} teamspaceId={} activeSessions=?", draftId, teamspaceId);
            teamspaceEventPublisher.publishDraftReady(teamspaceId, document.getId(), draft.getId(), content);
            publishDraftReadyToDocument(document.getId(), draft.getId(), content);
            log.warn("[DRAFT] generation complete draftId={}", draftId);

            if (document.getType() == com.aidea.aidea.domain.documents.entity.DocumentType.IDEA) {
                final String ideaContent = content;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        log.warn("[DRAFT] IDEA done - triggering pending drafts teamspaceId={}", teamspaceId);
                        triggerPendingDraftsWithIdeaContent(teamspaceId, teamspaceName, ideaContent);
                    }
                });
            }

        } catch (Exception e) {
            log.error("[DRAFT] generation failed draftId={}", draftId, e);

            String errorCode = "DRAFT_GENERATION_FAILED";
            if (e instanceof HttpClientErrorException httpEx
                    && httpEx.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                errorCode = "QUOTA_EXCEEDED";
            }

            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }
            draft.setErrorMessage(errorMsg);
            draft.setStatus(DraftStatus.FAILED);
            document.setStatus(DocumentAiStatus.IDLE);

            log.warn("[DRAFT] publishing draft:error draftId={} teamspaceId={} errorCode={}", draftId, teamspaceId, errorCode);
            teamspaceEventPublisher.publishDraftError(teamspaceId, document.getId(), errorCode);
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

            teamspaceEventPublisher.publishDraftReady(teamspaceId, document.getId(), draft.getId(), content);
            publishDraftReadyToDocument(document.getId(), draft.getId(), content);
            log.warn("[DRAFT] pending draft complete draftId={}", draftId);

        } catch (Exception e) {
            log.error("[DRAFT] pending draft failed draftId={}", draftId, e);

            String errorCode = "DRAFT_GENERATION_FAILED";
            if (e instanceof HttpClientErrorException httpEx
                    && httpEx.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                errorCode = "QUOTA_EXCEEDED";
            }

            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }
            draft.setErrorMessage(errorMsg);
            draft.setStatus(DraftStatus.FAILED);
            document.setStatus(DocumentAiStatus.IDLE);

            teamspaceEventPublisher.publishDraftError(teamspaceId, document.getId(), errorCode);
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

    private void publishDraftReadyToDocument(String documentId, String draftId, String content) {
        try {
            Map<String, Object> event = new java.util.LinkedHashMap<>();
            event.put("type", "draft:ready");
            event.put("draftId", draftId);
            event.put("content", content);
            draftEventPublisher.publishDraftToDocument(documentId, objectMapper.writeValueAsString(event));
            log.warn("[DRAFT] published draft:ready to document WS documentId={} draftId={}", documentId, draftId);
        } catch (Exception e) {
            log.error("[DRAFT] failed to publish draft:ready to document WS documentId={}", documentId, e);
        }
    }

    private String buildPrompt(com.aidea.aidea.domain.documents.entity.DocumentType type,
                                String ideaContext, String teamspaceName) {
        String typeInstruction = switch (type) {
            case IDEA          -> "서비스 아이디어 기획서 (핵심 가치, 타깃 사용자, 차별점 포함)";
            case PLAN          -> "프로젝트 계획서 (목표, 주요 기능, 개발 단계, 예상 일정 포함)";
            case USER_SCENARIO -> "유저 시나리오 문서 (주요 사용자 유형, Use Case 3~5개 포함)";
            case API_SPEC      -> "REST API 명세서 (주요 엔드포인트, 요청/응답 형식 포함)";
            case ERD           -> "ERD 설명 문서 (주요 엔티티, 핵심 필드, 관계 포함)";
        };

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
            - 섹션 제목은 구체적으로 작성
            - 전체 길이 1000자
            """.formatted(typeInstruction, projectSection, ideaSection);
    }

    private String callGeminiApi(String prompt) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
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
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini 빈 응답");
        }

        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) throw new IllegalStateException("Gemini content 필드 없음");

        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) throw new IllegalStateException("Gemini parts 필드 없음");

        String raw = parts.stream()
                .filter(p -> !Boolean.TRUE.equals(p.get("thought")))
                .map(p -> (String) p.get("text"))
                .filter(t -> t != null && !t.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Gemini text 파트 없음"));

        return stripMarkdownFence(raw);
    }
}
