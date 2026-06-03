# AI 피드백 시스템 분석 문서

## 목차
1. [개요](#1-개요)
2. [아키텍처](#2-아키텍처)
3. [상태 머신](#3-상태-머신)
4. [전체 로직 흐름](#4-전체-로직-흐름)
5. [핵심 컴포넌트 분석](#5-핵심-컴포넌트-분석)
6. [현재 문제점](#6-현재-문제점)
7. [개선 방향](#7-개선-방향)
8. [피드백 퀄리티 향상 방안](#8-피드백-퀄리티-향상-방안)

---

## 1. 개요

Aidea의 AI 피드백 시스템은 사용자가 작성한 IT 기획 문서를 Gemini AI가 검토하고, 개선된 수정안을 제안하는 기능이다. 문서가 충분히 구체적이면 즉시 수정안을 생성하고, 내용이 빈약하면 핵심 정보를 묻는 질문을 먼저 생성한 후 답변을 받아 수정안을 완성하는 2-pass 방식으로 동작한다.

**지원 모델**: Gemini 2.5 Flash (기본값, `GEMINI_MODEL` 환경변수로 변경 가능)

---

## 2. 아키텍처

```
클라이언트 (BlockNote)
    │
    │ REST API
    ▼
FeedbackController
    │
    ▼
FeedbackService  ──────────────────────► GeminiService (@Async)
    │                                         │
    │ WebSocket                               │ Gemini REST API
    ▼                                         ▼
FeedbackEventPublisher ◄──── DocumentWebSocketHandler (구현체)
    │
    ▼
클라이언트 (실시간 이벤트 수신)
```

### 관련 파일 목록

| 파일 | 역할 |
|------|------|
| `controller/FeedbackController.java` | REST API 엔드포인트 (5개) |
| `service/FeedbackService.java` | 비즈니스 로직, 상태 전이 관리 |
| `service/GeminiService.java` | Gemini API 호출, 프롬프트 생성 |
| `service/FeedbackEventPublisher.java` | WebSocket 이벤트 발행 인터페이스 |
| `service/dto/GeminiResult.java` | Gemini 응답 DTO |
| `entity/Feedback.java` | 피드백 엔티티 (DB 매핑) |
| `entity/FeedbackStatus.java` | 상태 enum |
| `entity/Question.java` | AI가 생성하는 질문 record |
| `entity/Answer.java` | 사용자 답변 record |
| `repository/FeedbackRepository.java` | JPA 레포지토리 |
| `converter/QuestionsConverter.java` | Question 리스트 ↔ JSON 컬럼 변환 |
| `converter/AnswersConverter.java` | Answer 리스트 ↔ JSON 컬럼 변환 |
| `global/util/YjsTextExtractor.java` | Yjs 바이너리 → 마크다운 텍스트 추출 |
| `documents/websocket/DocumentWebSocketHandler.java` | FeedbackEventPublisher 구현체 |

---

## 3. 상태 머신

```
              POST /feedback 요청
                     │
                     ▼
               [PENDING]  ─────────── Gemini 첫 호출 중
                     │
          ┌──────────┴──────────┐
          │ 문서 빈약             │ 문서 충분
          ▼                     ▼
    [QUESTIONING]          [DONE] ─── 수정안 생성 완료
          │                     │
POST /answers 제출        ┌──────┴──────┐
          │               │             │
          ▼               ▼             ▼
    [ANSWERING]      [ACCEPTED]    [REJECTED]
          │         (프론트가 적용)  (원본 유지)
          │
     Gemini 재호출
          │
          ▼
       [DONE]
          │ (위와 동일)
    
[FAILED] ← 어느 단계에서든 Gemini API 예외 발생 시
```

### 상태별 의미

| 상태 | 설명 | 다음 가능한 상태 |
|------|------|-----------------|
| `PENDING` | Gemini 첫 호출 진행 중 | `QUESTIONING`, `DONE`, `FAILED` |
| `QUESTIONING` | 질문 생성 완료, 사용자 답변 대기 | `ANSWERING` |
| `ANSWERING` | 사용자 답변 수신, Gemini 재호출 중 | `DONE`, `FAILED` |
| `DONE` | 수정안 생성 완료, 사용자 검토 대기 | `ACCEPTED`, `REJECTED` |
| `ACCEPTED` | 수정안 채택 (종료) | - |
| `REJECTED` | 원본 유지 (종료) | - |
| `FAILED` | Gemini API 오류 (종료) | - |

---

## 4. 전체 로직 흐름

### 4-1. 피드백 요청 (`POST /api/documents/{docId}/feedback`)

```
1. 문서 존재 확인
2. 권한 확인 (OWNER 또는 MEMBER만 가능, VIEWER 불가)
3. 문서 AI 상태 확인 → DRAFT 진행 중이면 CONFLICT 오류
4. 동시 진행 중인 피드백 존재 여부 확인 (PENDING/QUESTIONING/ANSWERING/DONE 상태)
   → 이미 있으면 CONFLICT 오류
5. Yjs 바이너리(snapshot + pending updates)를 YjsTextExtractor로 마크다운 텍스트 추출
   → 추출 결과가 비면 "# {문서제목}"을 기본값으로 사용
6. 문서 타입이 IDEA가 아닌 경우, 같은 팀스페이스의 IDEA 문서 내용을 별도 추출 (컨텍스트로 활용)
7. Feedback 엔티티 생성 (status=PENDING) 및 DB 저장
8. feedback:started WebSocket 이벤트 발행
9. 트랜잭션 커밋 후(afterCommit) GeminiService.callGemini() 비동기 호출
10. HTTP 202 Accepted + feedbackId 반환
```

**트랜잭션 커밋 이후 비동기 호출하는 이유**: Gemini 호출이 DB 커밋 전에 시작되면, 비동기 스레드에서 `findById`를 호출할 때 아직 커밋되지 않은 행을 찾지 못하는 경쟁 조건이 발생할 수 있다. `TransactionSynchronization.afterCommit()`으로 이를 방지한다.

### 4-2. Gemini 첫 호출 (`GeminiService.callGemini`)

```
1. feedbackId로 Feedback 엔티티 재조회
2. buildInitialPrompt()로 프롬프트 생성:
   - [ROLE]: IT 기획 문서 검토 전문 컨설턴트 역할 지정
   - [CONTEXT]: 응답 방식 (FEEDBACK 또는 QUESTIONS) 안내
   - [IDEA DOCUMENT]: 있는 경우 아이디어 문서 내용 포함
   - [DOCUMENT]: 원본 마크다운
   - [ADDITIONAL REQUEST]: 사용자 추가 요청사항 (최대 500자)
   - [INSTRUCTION]: 판단 기준 및 출력 형식 지시
3. callGeminiApi()로 Gemini REST API 호출:
   - responseMimeType: application/json (JSON 모드)
   - responseSchema: type(FEEDBACK|QUESTIONS), revisedMarkdown, questions 스키마 강제
   - thinkingBudget: 0 (thinking 비활성화 → 속도 우선)
   - maxOutputTokens: 8192
4. 응답 파싱 후 분기:
   - QUESTIONS → applyQuestioningResult(): status=QUESTIONING, feedback:questioning 이벤트 발행
   - FEEDBACK  → applyFeedbackResult(): status=DONE, feedback:ready 이벤트 발행
5. 예외 발생 시 → applyFailure(): status=FAILED, feedback:error 이벤트 발행
```

### 4-3. 답변 제출 (`POST /api/feedbacks/{feedbackId}/answers`)

```
1. feedbackId로 Feedback 조회
2. 권한 확인
3. 현재 상태가 QUESTIONING인지 확인 (아니면 FEEDBACK_INVALID_STATUS 오류)
4. 답변 리스트를 Answer 객체로 변환하여 feedback.answers에 저장
5. status = ANSWERING
6. 트랜잭션 커밋 후 GeminiService.callGeminiWithAnswers() 비동기 호출
7. HTTP 202 Accepted 반환
```

### 4-4. Gemini 재호출 (`GeminiService.callGeminiWithAnswers`)

```
1. buildAnswerPrompt()로 두 번째 프롬프트 생성:
   - [ORIGINAL DOCUMENT] + [QUESTIONS AND ANSWERS] (Q/A 쌍 나열)
   - [ADDITIONAL REQUEST]
   - [INSTRUCTION]: 답변을 원본에 자연스럽게 녹여 수정안 작성
2. callGeminiApi() 호출
3. 응답이 반드시 FEEDBACK 타입이어야 함 (아니면 IllegalStateException → FAILED)
4. applyFeedbackResult(): status=DONE, feedback:ready 이벤트 발행
```

### 4-5. 수락/거부

- `POST /api/feedbacks/{feedbackId}/accept` → status = ACCEPTED, `feedback:resolved {outcome:"ACCEPTED"}` 이벤트
- `POST /api/feedbacks/{feedbackId}/reject` → status = REJECTED, `feedback:resolved {outcome:"REJECTED"}` 이벤트
- 수정안 실제 적용(BlockNote 문서 내용 변경)은 **프론트엔드 책임**이며, 백엔드는 상태만 관리한다.

### 4-6. WebSocket 연결 시 피드백 상태 복원

신규 WebSocket 연결 시 `doc:init` 이벤트에 `activeFeedback` 필드가 포함된다.

```java
// ACCEPTED, REJECTED, FAILED를 제외한 진행 중인 피드백 조회
feedbackRepository.findTopByDocumentIdAndStatusNotInOrderByCreatedAtDesc(docId, TERMINAL_STATUSES)
```

응답 내용:
- `feedbackId`, `status`, `revisedMarkdown` (DONE일 때)
- `questions` (QUESTIONING일 때만)

---

## 5. 핵심 컴포넌트 분석

### 5-1. YjsTextExtractor

Yjs v1 바이너리 포맷을 직접 파싱하는 커스텀 파서. 공식 JS 구현을 Java로 포팅했다.

- snapshot + 모든 pending updates를 순서대로 파싱
- Delete Set을 합산하여 삭제된 ContentString 항목 제거
- GC(0), Skip(10), ContentDeleted(1), ContentString(4) 등 주요 타입 지원

**주의**: ContentJSON(2, deprecated), ContentDoc(9)는 미지원이며, 해당 타입을 만나면 파싱을 중단한다. 예외 발생 시 조용히 무시하고 이미 수집된 items를 사용한다.

### 5-2. Gemini JSON 스키마 강제

`buildResponseSchema()`로 Gemini 출력을 특정 JSON 구조로 강제한다. 이를 통해 별도 파싱 없이 `ObjectMapper.readValue()`로 바로 역직렬화가 가능하다.

```
{
  "type": "FEEDBACK" | "QUESTIONS",
  "revisedMarkdown": "...",  // FEEDBACK일 때
  "questions": [             // QUESTIONS일 때
    {
      "id": "q1",
      "section": "핵심 기능",
      "text": "질문 내용",
      "options": ["선택지1", "선택지2"]  // optional
    }
  ]
}
```

### 5-3. FeedbackEventPublisher 인터페이스 분리

`GeminiService`와 `FeedbackService`는 `FeedbackEventPublisher` 인터페이스에만 의존하고, `DocumentWebSocketHandler`가 이를 구현한다. WebSocket 인프라와 AI 피드백 도메인을 디커플링하여 단위 테스트 시 Mock으로 교체 가능하다.

### 5-4. 동시 피드백 방지

```java
boolean inProgress = feedbackRepository.existsByDocumentIdAndStatusIn(docId, IN_PROGRESS_STATUSES);
```

`IN_PROGRESS_STATUSES` = `[PENDING, QUESTIONING, ANSWERING, DONE]`

하나의 문서에 동시에 진행 중인 피드백이 하나만 존재하도록 보장한다. 단, 이 체크는 애플리케이션 레벨 guard이므로 DB 유니크 제약이 없다.

---

## 6. 현재 문제점

### 6-1. [심각] 동시성 — DB 유니크 제약 부재

**현상**: `existsByDocumentIdAndStatusIn` 체크는 단순 조회다. 두 요청이 동시에 들어오면 두 체크 모두 `false`를 반환하고 피드백이 중복 생성될 수 있다.

**영향**: 같은 문서에 피드백이 두 개 동시 진행 → 두 개의 Gemini 호출, 두 개의 WebSocket 이벤트, DB 상태 충돌.

**해결**: `feedbacks` 테이블에 **부분 유니크 인덱스** 추가 (status IN ('PENDING','QUESTIONING','ANSWERING','DONE')), 또는 `SELECT ... FOR UPDATE` 비관적 락 적용.

### 6-2. [중간] RestClient가 Bean이 아닌 필드 초기화

```java
private final RestClient restClient = RestClient.builder()
        .requestFactory(new JdkClientHttpRequestFactory(...))
        .build();
```

`GeminiService`가 Spring Bean임에도 `RestClient`를 필드 초기화로 생성한다. Spring의 생명주기 관리 밖에 있으며, `@Value`로 주입된 `apiKey` 등을 사용하는 메서드가 `RestClient`에 접근 불가능하다. URL/키 설정을 RestClient 레벨로 이동하거나, `@Configuration`에서 Bean으로 등록해야 한다.

### 6-3. [중간] @Async 메서드에 @Transactional 동시 사용

```java
@Async
@Transactional
public void callGemini(String feedbackId) { ... }
```

`@Async`는 새 스레드에서 메서드를 실행한다. Spring의 `@Transactional` 트랜잭션 전파는 스레드-로컬 기반이므로, 비동기 메서드는 새 트랜잭션을 시작한다. 이 자체는 동작하지만, 예외가 발생해도 호출자 트랜잭션으로 롤백이 전파되지 않는다. Gemini 호출 중 예외가 발생하면 `applyFailure()`로 상태를 FAILED로 변경하는데, 이 변경도 해당 트랜잭션 범위에서만 처리된다.

현재 로직상 큰 문제는 없지만, `applyQuestioningResult`/`applyFeedbackResult`/`applyFailure` 내에서 이벤트 발행 후 상태 저장이 실패하는 경우 WebSocket 이벤트는 보냈는데 DB 상태가 안 바뀌는 불일치가 발생할 수 있다.

### 6-4. [중간] 질문 응답 검증 없음

`submitAnswer`에서 제출된 답변의 `questionId`가 실제로 해당 피드백의 질문 목록에 존재하는지 검증하지 않는다. 클라이언트가 임의의 `questionId`를 보내도 그대로 저장되고, Gemini 재호출 시 "답변 없음"으로 처리된다.

### 6-5. [낮음] FAILED 상태의 재시도 불가

Gemini 호출이 일시적 네트워크 오류로 실패하면 `FAILED`가 되고, 사용자는 피드백을 다시 요청해야 한다. FAILED 상태에서 재시도 API가 없으며, 새 피드백을 생성해야 한다. (새 피드백 생성은 가능하다 — FAILED는 `IN_PROGRESS_STATUSES`에 포함되지 않으므로.)

### 6-6. [낮음] Gemini API 타임아웃 설정 불완전

```java
HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build()
```

`connectTimeout`만 설정되어 있고, 읽기 타임아웃(read timeout)이 없다. Gemini가 응답을 시작했지만 매우 느리게 전송하는 경우 스레드가 무기한 블록될 수 있다. 비동기 풀 스레드 고갈로 이어질 수 있다.

### 6-7. [낮음] thinkingBudget=0 하드코딩

```java
"thinkingConfig", Map.of("thinkingBudget", 0)
```

`application.yml`에 `gemini.thinking-budget` 설정이 있지만 `GeminiService`에서 읽지 않고 0으로 하드코딩되어 있다.

### 6-8. [낮음] YjsTextExtractor 예외 무시

```java
} catch (Exception ignored) {
    // 파싱 중 범위 초과 등 → 이미 수집된 items 사용
}
```

파싱 오류가 조용히 무시된다. 추출된 텍스트가 불완전해도 빈 문자열 체크만 하므로, 일부만 추출된 상태로 Gemini에 전달될 수 있다.

---

## 7. 개선 방향

### 7-1. DB 레벨 동시성 제어

```sql
-- 부분 유니크 인덱스 (MySQL 8.0+에서는 표현식 인덱스로 구현)
-- 또는 application level SELECT FOR UPDATE
ALTER TABLE feedbacks ADD INDEX idx_doc_active_status (document_id, status);
```

`FeedbackService.initiateFeedback()`에 비관적 락 적용:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Feedback> findByDocumentIdAndStatusIn(String docId, List<FeedbackStatus> statuses);
```

### 7-2. RestClient를 Spring Bean으로 관리

```java
@Configuration
public class GeminiClientConfig {
    @Bean
    public RestClient geminiRestClient(@Value("${gemini.base-url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()
                ))
                .build();
    }
}
```

### 7-3. 읽기 타임아웃 추가

Gemini 응답 대기 시간을 제한하여 스레드 고갈을 방지한다.

```java
HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    // JDK 21+ 또는 별도 타임아웃 처리
    .build()
```

또는 `RestClient`의 `timeout` 설정(Spring 6.1+)을 사용한다.

### 7-4. thinkingBudget 설정값 활용

```java
@Value("${gemini.thinking-budget:0}")
private int thinkingBudget;

// 사용 시
"thinkingConfig", Map.of("thinkingBudget", thinkingBudget)
```

### 7-5. 질문 ID 검증 추가

```java
Set<String> validIds = feedback.getQuestions().stream()
        .map(Question::id)
        .collect(Collectors.toSet());
boolean allValid = request.answer().stream()
        .allMatch(a -> validIds.contains(a.questionId()));
if (!allValid) throw new CustomException(ErrorCode.FEEDBACK_INVALID_ANSWER);
```

### 7-6. 이벤트 발행과 상태 저장 순서 보장

`applyQuestioningResult` / `applyFeedbackResult`에서 상태 저장 후 이벤트를 발행하도록 순서를 보장한다. 현재는 `feedback.setStatus()` 호출 직후 이벤트를 발행하는데, JPA의 dirty checking에 의한 실제 DB 저장은 트랜잭션 커밋 시점이므로 큰 문제는 없다. 다만 WebSocket 이벤트를 `TransactionSynchronization.afterCommit()` 시점에 발행하면 DB 저장과 이벤트 발행의 순서를 완전히 보장할 수 있다.

---

## 8. 피드백 퀄리티 향상 방안

### 8-1. 시스템 프롬프트 분리 (System Instruction)

현재 역할 지시(`[ROLE]`, `[CONTEXT]`)가 사용자 메시지(contents)에 포함되어 있다. Gemini API는 `systemInstruction` 필드를 별도로 지원하므로, 불변적인 역할 정의를 분리하면 프롬프트 토큰을 절약하고 역할 부여가 더 확실하게 된다.

```java
Map<String, Object> requestBody = Map.of(
    "systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
    "contents", List.of(Map.of("parts", List.of(Map.of("text", userPrompt)))),
    ...
);
```

### 8-2. 문서 타입별 맞춤 평가 기준

현재 모든 문서 타입(PLAN, USER_SCENARIO, API_SPEC, ERD)에 동일한 프롬프트를 사용한다. 문서 타입에 따라 평가 기준이 달라야 한다.

| 문서 타입 | 핵심 평가 항목 |
|-----------|---------------|
| `PLAN` | 핵심 기능, 타깃 사용자, 차별점, 로드맵 |
| `USER_SCENARIO` | 사용자 페르소나, 시나리오 흐름, 엣지 케이스 |
| `API_SPEC` | 엔드포인트 완성도, 에러 케이스, 인증 방식 |
| `ERD` | 정규화 수준, 관계 정의, 인덱스 전략 |

```java
private String buildInitialPrompt(String originalMarkdown, String additionalRequest, 
                                   String ideaMarkdown, DocumentType docType) {
    String typeSpecificCriteria = getTypeSpecificCriteria(docType);
    // ...
}
```

### 8-3. 빈약 판단 기준 정교화

현재 기준:
- 핵심 기능/타깃 사용자/차별점 중 2개 이상이 한 줄 미만
- 전체 200자 미만

이 기준은 문서 구조를 고려하지 않는다. 예를 들어 ERD 문서는 200자 미만이어도 충분할 수 있다. 개선안:

- 문서 타입별 최소 필수 섹션 목록 정의
- 필수 섹션 누락 여부로 판단
- 섹션별 최소 내용량 기준 설정

### 8-4. 수정안 품질 피드백 루프

현재 ACCEPTED/REJECTED만 기록하고 어떤 수정이 좋았는지 정보가 없다. 향후 개선 방향:

- REJECTED 시 거부 이유를 선택하는 필드 추가 (`rejectionReason: "IRRELEVANT" | "WRONG_DIRECTION" | ...`)
- 반복적인 REJECTED 패턴을 프롬프트에 반영

### 8-5. 청킹과 컨텍스트 윈도우 관리

`maxOutputTokens: 8192`로 제한되어 있다. 문서가 매우 길거나 IDEA 문서까지 포함되면 입력 토큰이 많아진다. 개선 방안:

- 입력 텍스트 길이 사전 체크 후 필요 시 섹션별 분할 처리
- IDEA 문서는 전체가 아닌 요약본만 포함 (별도 요약 API 호출 또는 첫 N자 추출)
- 현재 `originalMarkdown`이 빈 경우 `"# {제목}"`만 전달하는 fallback이 있는데, 이때도 AI 호출을 하는 것이 적합한지 재검토 필요

### 8-6. Gemini thinking 활용

`thinkingBudget: 0`으로 thinking이 비활성화되어 있다. 피드백 퀄리티를 높이려면 thinking을 활성화하는 것이 효과적이다.

- `thinkingBudget: -1` → 자동 (Gemini가 필요에 따라 결정)
- `thinkingBudget: 1024` 이상 → 명시적 thinking 허용

단, thinking을 사용하면 응답 시간이 늘어날 수 있으므로 사용자 경험과 트레이드오프를 고려해야 한다. 현재 `application.yml`에 `GEMINI_THINKING_BUDGET` 설정은 준비되어 있으므로, **6-7번 문제** 수정으로 즉시 활성화 가능하다.

### 8-7. 수정안 diff 제공

현재 수정안은 전체 마크다운을 그대로 반환한다. 원본과 수정안의 차이점을 명시적으로 표시하면 사용자가 변경 내용을 더 쉽게 파악할 수 있다.

프롬프트에 추가:
```
수정한 부분에는 반드시 변경 이유를 짧게 코멘트로 달아라.
예: <!-- [개선] 타깃 사용자를 더 구체적으로 명시 -->
```

### 8-8. 질문 생성 품질

현재 질문은 3~5개, 객관식 선택지 포함이다. 개선 방향:

- **질문 우선순위 필드 추가**: `priority: HIGH | MEDIUM | LOW` — 핵심적인 질문을 먼저 보여줄 수 있음
- **선택지 + 직접입력 혼합 가이드**: 선택지가 있어도 "직접 입력" 옵션을 항상 포함하도록 프롬프트에 명시
- **섹션별 1개 제한**: 같은 섹션에 대한 질문이 중복되지 않도록 프롬프트에 명시

---

## 참고: WebSocket 이벤트 목록

| 이벤트 타입 | 발행 시점 | 페이로드 |
|------------|----------|---------|
| `feedback:started` | 피드백 요청 접수 직후 | `feedbackId`, `requestedBy` |
| `feedback:questioning` | 질문 생성 완료 | `feedbackId`, `questions[]` |
| `feedback:ready` | 수정안 생성 완료 | `feedbackId`, `revisedMarkdown` |
| `feedback:resolved` | 수락 또는 거부 | `feedbackId`, `outcome` |
| `feedback:error` | Gemini API 실패 | `feedbackId` |
