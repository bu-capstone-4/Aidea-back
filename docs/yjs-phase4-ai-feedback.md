# Gito Phase 4 — AI 피드백

> **전제 조건:** [Phase 1](./gito-phase1-foundation.md) + [Phase 2](./gito-phase2-websocket.md) 완료
> - Phase 1: `Document` 엔티티, JWT 인증, 팀스페이스 권한 구현 완료
> - Phase 2: `DocumentWebSocketHandler.pushToDocument()` 구현 완료 (피드백 이벤트 푸시에 사용)
>
> **필독:** 이 문서를 읽기 전에 [gito-index.md](./gito-index.md)를 반드시 먼저 읽어라.
> 특히 **AI 피드백 이벤트 프로토콜** (`feedback:questioning`, `feedback:ready`) 섹션을 확인할 것.

---

## 완료 조건

이 Phase가 끝나면 다음이 가능해야 한다:
- `POST /api/documents/{docId}/feedback` 호출 시 즉시 202가 반환되고 비동기로 Gemini가 호출됨
- 문서가 충분하면 `feedback:ready` 이벤트가 WebSocket으로 브로드캐스트됨
- 문서가 빈약하면 `feedback:questioning` 이벤트로 질문 목록이 전달됨
- `POST /api/feedbacks/{feedbackId}/answer` 로 답변 제출 시 Gemini를 재호출해 피드백 생성
- `POST /api/feedbacks/{feedbackId}/accept` 로 유저가 피드백 버전을 수락함
- 같은 문서에 진행 중인 피드백이 있으면 중복 요청이 거부됨 (409 반환)

---

## 피드백 상태 흐름

```
POST /api/documents/{docId}/feedback
    │
    ├─ Feedback 생성 (status: PENDING)
    └─ @Async GeminiService.callGemini() 호출
            │
            ├─ [문서 충분] Gemini → FEEDBACK 타입 응답
            │   status: DONE
            │   WebSocket: feedback:ready { feedbackId, yjsBinary }
            │
            └─ [문서 빈약] Gemini → QUESTIONS 타입 응답
                status: QUESTIONING
                WebSocket: feedback:questioning { feedbackId, questions }
                │
                └─ POST /api/feedbacks/{feedbackId}/answer
                    status: ANSWERING
                    @Async GeminiService.callGeminiWithAnswers() 호출
                            │
                            └─ status: DONE
                               WebSocket: feedback:ready { feedbackId, yjsBinary }

POST /api/feedbacks/{feedbackId}/accept
    → status: ACCEPTED
```

**상태 ENUM:**
```
PENDING → QUESTIONING → ANSWERING → DONE → ACCEPTED
         └──────────────────────────┘
              (문서 충분 시 바로 DONE)
```

---

## 1. Feedback 엔티티

```java
@Entity
@Table(name = "feedbacks")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback {

    @Id
    private String id; // UUID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeedbackStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "additional_request", columnDefinition = "TEXT")
    private String additionalRequest; // 유저가 입력한 추가 요청사항 (optional)

    // JSON — Gemini가 생성한 질문 목록 (status: QUESTIONING 이후 채워짐)
    @Column(name = "questions", columnDefinition = "JSON")
    @Convert(converter = QuestionsConverter.class)
    private List<Question> questions;

    // JSON — 유저가 제출한 답변 목록 (status: ANSWERING 이후 채워짐)
    @Column(name = "answers", columnDefinition = "JSON")
    @Convert(converter = AnswersConverter.class)
    private List<Answer> answers;

    // DONE 이후 채워지는 Yjs 피드백 바이너리
    @Column(name = "yjs_binary", columnDefinition = "LONGBLOB")
    private byte[] yjsBinary;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static Feedback create(String id, Document document, User requestedBy, String additionalRequest) {
        Feedback f = new Feedback();
        f.id = id;
        f.document = document;
        f.requestedBy = requestedBy;
        f.additionalRequest = additionalRequest;
        f.status = FeedbackStatus.PENDING;
        f.createdAt = LocalDateTime.now();
        return f;
    }
}
```

### FeedbackStatus ENUM

```java
public enum FeedbackStatus {
    PENDING,       // Gemini 호출 중
    QUESTIONING,   // 문서 빈약 판단 — 질문 목록 생성 완료
    ANSWERING,     // 유저 답변 제출 — Gemini 재호출 중
    DONE,          // 피드백 생성 완료 — 유저 검토 대기
    ACCEPTED       // 유저가 버전 선택 완료
}
```

---

## 2. JSON 타입 정의 및 컨버터

### Question / Answer 레코드

```java
// Gemini가 생성하는 질문
public record Question(
    String id,          // "q1", "q2" ...
    String section,     // 빈약하다고 판단된 섹션명 (예: "핵심 기능")
    String text,        // 실제 질문 텍스트
    List<String> options // 선택지 (없으면 null — 직접 입력만)
) {}

// 유저가 제출하는 답변
public record Answer(
    String questionId, // Question.id 참조
    String value       // 선택하거나 직접 입력한 값
) {}
```

### QuestionsConverter

```java
@Converter
public class QuestionsConverter implements AttributeConverter<List<Question>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<Question> questions) {
        if (questions == null) return null;
        try {
            return objectMapper.writeValueAsString(questions);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Question 직렬화 실패", e);
        }
    }

    @Override
    public List<Question> convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return objectMapper.readValue(dbData, new TypeReference<List<Question>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Question 역직렬화 실패", e);
        }
    }
}
```

`AnswersConverter`도 동일한 패턴으로 구현. `List<Question>` → `List<Answer>`만 변경.

---

## 3. FeedbackRepository

```java
public interface FeedbackRepository extends JpaRepository<Feedback, String> {

    // 진행 중인 피드백 존재 여부 확인 (동시 요청 방지용)
    boolean existsByDocumentIdAndStatusIn(String documentId, List<FeedbackStatus> statuses);

    // 피드백 상태 조회
    Optional<Feedback> findById(String feedbackId);
}
```

---

## 4. FeedbackService — 상태 머신

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final GeminiService geminiService;

    // 피드백 요청 시작 — 즉시 반환, Gemini는 비동기
    @Transactional
    public FeedbackIdResponse initiateFeedback(String docId, FeedbackRequest req, String userId) {
        // 진행 중인 피드백이 있으면 중복 요청 거부
        boolean hasInProgress = feedbackRepository.existsByDocumentIdAndStatusIn(
            docId, List.of(FeedbackStatus.PENDING, FeedbackStatus.QUESTIONING, FeedbackStatus.ANSWERING)
        );
        if (hasInProgress) {
            throw new ConflictException("이미 진행 중인 피드백이 있습니다");
        }

        Document document = documentRepository.findById(docId)
            .orElseThrow(() -> new NotFoundException("문서를 찾을 수 없습니다"));
        User user = userRepository.findById(userId).orElseThrow();

        Feedback feedback = Feedback.create(
            UUID.randomUUID().toString(), document, user, req.getAdditionalRequest()
        );
        feedbackRepository.save(feedback);

        // 비동기 호출 — documentText는 클라이언트가 요청 body에 포함해서 보냄
        // 서버는 Yjs를 파싱할 수 없으므로 클라이언트가 plain text를 추출해 전달
        geminiService.callGemini(feedback.getId(), req.getDocumentText(), req.getAdditionalRequest());

        return new FeedbackIdResponse(feedback.getId(), FeedbackStatus.PENDING);
    }

    // 유저 답변 제출 — 즉시 ANSWERING으로 변경 후 Gemini 재호출
    @Transactional
    public FeedbackStatusResponse submitAnswer(String feedbackId, List<Answer> answers) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
            .orElseThrow(() -> new NotFoundException("피드백을 찾을 수 없습니다"));

        if (feedback.getStatus() != FeedbackStatus.QUESTIONING) {
            throw new BadRequestException("답변 제출은 QUESTIONING 상태에서만 가능합니다");
        }

        feedback.setAnswers(answers);
        feedback.setStatus(FeedbackStatus.ANSWERING);

        // 비동기 재호출 — 질문 + 답변 포함
        geminiService.callGeminiWithAnswers(
            feedback.getId(),
            /* documentText — FeedbackService가 문서 텍스트를 저장하거나 다시 받아야 함 */
            feedback.getDocument().getTitle(), // 임시: 실제 구현 시 요청에서 받거나 별도 저장
            feedback.getQuestions(),
            answers
        );

        return new FeedbackStatusResponse(feedbackId, FeedbackStatus.ANSWERING);
    }

    // 유저가 피드백 버전 수락
    @Transactional
    public FeedbackStatusResponse acceptFeedback(String feedbackId) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
            .orElseThrow(() -> new NotFoundException("피드백을 찾을 수 없습니다"));

        if (feedback.getStatus() != FeedbackStatus.DONE) {
            throw new BadRequestException("수락은 DONE 상태에서만 가능합니다");
        }

        feedback.setStatus(FeedbackStatus.ACCEPTED);
        return new FeedbackStatusResponse(feedbackId, FeedbackStatus.ACCEPTED);
    }
}
```

---

## 5. GeminiService — 비동기 Gemini 호출

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final RestClient restClient;
    private final FeedbackRepository feedbackRepository;
    private final DocumentWebSocketHandler webSocketHandler; // Phase 2에서 구현됨

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    // 최초 피드백 요청 (답변 없이)
    @Async
    @Transactional
    public void callGemini(String feedbackId, String documentText, String additionalRequest) {
        Feedback feedback = feedbackRepository.findById(feedbackId).orElseThrow();

        try {
            GeminiApiResponse response = requestToGemini(buildInitialPrompt(documentText, additionalRequest));

            if (response.isQuestionsType()) {
                // 문서 빈약 — 질문 생성
                feedback.setQuestions(response.getQuestions());
                feedback.setStatus(FeedbackStatus.QUESTIONING);
                feedbackRepository.save(feedback);

                // 해당 문서를 보고 있는 모든 세션에 feedback:questioning 이벤트 푸시
                webSocketHandler.pushToDocument(
                    feedback.getDocument().getId(),
                    buildQuestioningEvent(feedbackId, response.getQuestions())
                );

            } else {
                // 문서 충분 — 피드백 바이너리 생성 완료
                byte[] yjsBinary = response.getYjsBinary(); // Gemini가 반환한 Yjs 바이너리
                feedback.setYjsBinary(yjsBinary);
                feedback.setStatus(FeedbackStatus.DONE);
                feedbackRepository.save(feedback);

                webSocketHandler.pushToDocument(
                    feedback.getDocument().getId(),
                    buildReadyEvent(feedbackId, yjsBinary)
                );
            }
        } catch (Exception e) {
            log.error("Gemini 호출 실패: feedbackId={}", feedbackId, e);
            // 실패 시 PENDING 상태 유지 — 유저가 폴링으로 확인하거나 재시도
        }
    }

    // 답변 제출 후 재호출
    @Async
    @Transactional
    public void callGeminiWithAnswers(String feedbackId, String documentText,
                                      List<Question> questions, List<Answer> answers) {
        Feedback feedback = feedbackRepository.findById(feedbackId).orElseThrow();

        try {
            GeminiApiResponse response = requestToGemini(
                buildAnsweredPrompt(documentText, questions, answers)
            );

            // 답변이 있으면 Gemini는 항상 FEEDBACK 타입 반환 (QUESTIONS 타입 반환 없음)
            byte[] yjsBinary = response.getYjsBinary();
            feedback.setYjsBinary(yjsBinary);
            feedback.setStatus(FeedbackStatus.DONE);
            feedbackRepository.save(feedback);

            webSocketHandler.pushToDocument(
                feedback.getDocument().getId(),
                buildReadyEvent(feedbackId, yjsBinary)
            );
        } catch (Exception e) {
            log.error("Gemini 재호출 실패: feedbackId={}", feedbackId, e);
        }
    }

    private GeminiApiResponse requestToGemini(String prompt) {
        // RestClient로 Gemini API HTTP 호출
        // 구체적인 엔드포인트와 요청 형식은 Gemini API 문서 참고
        return restClient.post()
            .uri("https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + geminiApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))))
            .retrieve()
            .body(GeminiApiResponse.class);
    }

    // WebSocket 이벤트 JSON 생성 (index 문서의 프로토콜 참고)
    private String buildQuestioningEvent(String feedbackId, List<Question> questions) {
        try {
            return new ObjectMapper().writeValueAsString(Map.of(
                "type", "feedback:questioning",
                "feedbackId", feedbackId,
                "questions", questions
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String buildReadyEvent(String feedbackId, byte[] yjsBinary) {
        try {
            return new ObjectMapper().writeValueAsString(Map.of(
                "type", "feedback:ready",
                "feedbackId", feedbackId,
                "yjsBinary", Base64.getEncoder().encodeToString(yjsBinary)
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String buildInitialPrompt(String documentText, String additionalRequest) {
        // Gemini에게 전달할 프롬프트 구성
        // 문서가 빈약하면 {"type":"QUESTIONS","questions":[...]} 형태로 응답하도록 지시
        // 문서가 충분하면 {"type":"FEEDBACK","content":"..."} 형태로 응답하도록 지시
        StringBuilder sb = new StringBuilder();
        sb.append("다음 기획 문서를 검토해줘.\n\n").append(documentText);
        if (additionalRequest != null && !additionalRequest.isBlank()) {
            sb.append("\n\n추가 요청: ").append(additionalRequest);
        }
        // 응답 형식 지시 추가
        return sb.toString();
    }

    private String buildAnsweredPrompt(String documentText, List<Question> questions, List<Answer> answers) {
        // 최초 문서 + 질문 + 유저 답변을 모두 포함한 프롬프트
        return documentText; // 구체적 프롬프트 엔지니어링은 별도 구현
    }
}
```

---

## 6. FeedbackController

```java
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    // 피드백 요청 — 즉시 202 반환
    @PostMapping("/documents/{docId}/feedback")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FeedbackIdResponse requestFeedback(
            @PathVariable String docId,
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal String userId) {
        return feedbackService.initiateFeedback(docId, request, userId);
    }

    // 피드백 상태 폴링 (WebSocket 이벤트를 못 받은 경우 대비)
    @GetMapping("/feedbacks/{feedbackId}")
    public FeedbackStatusResponse getFeedbackStatus(@PathVariable String feedbackId) {
        return feedbackService.getStatus(feedbackId);
    }

    // 질문 답변 제출
    @PostMapping("/feedbacks/{feedbackId}/answer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FeedbackStatusResponse submitAnswer(
            @PathVariable String feedbackId,
            @Valid @RequestBody AnswerRequest request) {
        return feedbackService.submitAnswer(feedbackId, request.getAnswers());
    }

    // 피드백 버전 수락
    @PostMapping("/feedbacks/{feedbackId}/accept")
    public FeedbackStatusResponse acceptFeedback(@PathVariable String feedbackId) {
        return feedbackService.acceptFeedback(feedbackId);
    }
}
```

### DTO 정의

```java
// 피드백 요청 body
public record FeedbackRequest(
    String documentText,      // 클라이언트가 추출한 현재 문서 plain text (필수)
    String additionalRequest  // 유저 추가 요청 (optional)
) {}

// 답변 제출 body
public record AnswerRequest(List<Answer> answers) {}

// 응답
public record FeedbackIdResponse(String feedbackId, FeedbackStatus status) {}
public record FeedbackStatusResponse(String feedbackId, FeedbackStatus status) {}
```

---

## 7. application.yml 추가 설정

```yaml
gemini:
  api-key: ${GEMINI_API_KEY}  # 환경변수로 주입

spring:
  task:
    execution:
      pool:
        core-size: 4
        max-size: 8
        queue-capacity: 100
```

---

## 주의사항

### @Async + @Transactional 조합

`callGemini()`에 `@Async`와 `@Transactional`이 함께 붙어 있다. Spring은 두 AOP를 모두 적용할 수 있으나, 트랜잭션은 새 스레드에서 새 트랜잭션으로 시작됨을 인지해야 한다. 즉, 호출자(`FeedbackService.initiateFeedback`)의 트랜잭션과 별개의 트랜잭션으로 실행된다. 이는 의도된 동작이다.

### 피드백 동시 요청 방지

`existsByDocumentIdAndStatusIn`의 조회와 `Feedback.create` 저장 사이에 레이스 컨디션이 있을 수 있다. 높은 동시성이 요구된다면 `documents` 테이블에 `SELECT FOR UPDATE`를 적용하거나 `feedbacks` 테이블에 유니크 제약을 추가한다.

### documentText는 클라이언트가 전달

서버가 Yjs를 파싱할 수 없으므로 Gemini에게 넘길 문서 텍스트는 클라이언트가 `FeedbackRequest.documentText`로 전달한다. 클라이언트는 `ydoc.getXmlFragment('content').toString()`으로 plain text를 추출해 포함시킨다.

---

## Phase 4 완료 체크리스트

- [ ] `POST /api/documents/{docId}/feedback` 즉시 202 반환됨
- [ ] Gemini 호출 완료 후 `feedback:questioning` 또는 `feedback:ready` 이벤트가 WebSocket으로 전달됨
- [ ] `feedbacks` 테이블에 `questions`, `answers` JSON 컬럼이 정상 저장/조회됨
- [ ] `yjsBinary` LONGBLOB 컬럼이 정상 저장됨
- [ ] 진행 중인 피드백이 있는 상태에서 재요청 시 409 반환됨
- [ ] `POST /api/feedbacks/{feedbackId}/answer` 제출 후 `feedback:ready` 이벤트 수신됨
- [ ] `POST /api/feedbacks/{feedbackId}/accept` 후 DB `status`가 `ACCEPTED`로 변경됨
