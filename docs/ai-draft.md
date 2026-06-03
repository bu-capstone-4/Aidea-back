# AI 초안 생성 시스템 분석 문서

## 목차
1. [개요](#1-개요)
2. [아키텍처](#2-아키텍처)
3. [상태 흐름](#3-상태-흐름)
4. [전체 로직 흐름](#4-전체-로직-흐름)
5. [핵심 컴포넌트 분석](#5-핵심-컴포넌트-분석)
6. [현재 문제점](#6-현재-문제점)
7. [개선 방향](#7-개선-방향)
8. [초안 퀄리티 향상 방안](#8-초안-퀄리티-향상-방안)

---

## 1. 개요

팀스페이스를 생성할 때 사용자가 기획 문서 타입(IDEA, PLAN, USER_SCENARIO, API_SPEC, ERD)을 선택하면, Gemini AI가 각 문서에 대한 초안(Draft)을 자동으로 생성해준다. 초안은 사용자가 채워야 할 `[TODO]` 항목이 포함된 마크다운 형식으로 제공되며, 생성이 완료되면 WebSocket으로 클라이언트에 알린다.

**지원 모델**: Gemini 2.5 Flash (기본값, `GEMINI_MODEL` 환경변수로 변경 가능)

---

## 2. 아키텍처

```
클라이언트 (팀스페이스 생성 UI)
    │
    │ POST /api/teamspaces
    ▼
TeamSpaceController
    │
    ▼
TeamSpaceService
    │  documentTypes 순회, 각 문서마다 호출
    ▼
DraftService.triggerDraftGeneration()
    │  afterCommit()에 등록
    ▼
DraftAsyncExecutor.generateDraftAsync() (@Async)
    │
    │ Gemini REST API 호출
    ▼
FeedbackEventPublisher
    │  publishToDocument (문서 WebSocket 채널)
    ▼
DocumentWebSocketHandler → /ws/documents/{docId}
```

### 관련 파일 목록

| 파일 | 역할 |
|------|------|
| `teamspace/controller/TeamSpaceController.java` | 팀스페이스 생성 REST 엔드포인트 |
| `teamspace/service/TeamSpaceService.java` | 팀스페이스 + 문서 생성, DraftService 호출 |
| `teamspace/dto/TeamSpaceCreateRequest.java` | 요청 DTO (name, idea, documentTypes) |
| `teamspace/entity/TeamSpace.java` | 팀스페이스 엔티티 |
| `teamspace/entity/TeamSpaceStatus.java` | `CREATING` / `CREATED` enum |
| `teamspace/websocket/TeamspaceWebSocketHandler.java` | 팀스페이스 WebSocket 핸들러 |
| `draft/service/DraftService.java` | Draft 엔티티 생성, 비동기 실행 트리거 |
| `draft/service/DraftAsyncExecutor.java` | Gemini API 호출, 초안 생성 비동기 실행체 |
| `draft/entity/Draft.java` | 초안 엔티티 (DB 매핑) |
| `draft/entity/DraftStatus.java` | `PENDING` / `DONE` / `FAILED` enum |
| `draft/repository/DraftRepository.java` | JPA 레포지토리 |
| `documents/dto/ActiveDraftInfo.java` | WebSocket `doc:init` 응답용 DTO |

---

## 3. 상태 흐름

### Draft 상태

```
POST /api/teamspaces 요청
        │
        ▼
   (Draft 행 생성)
   [PENDING] ──── Gemini 호출 중
        │
   ┌────┴────┐
   │         │
   ▼         ▼
 [DONE]   [FAILED]
```

### Document.status (AI 처리 여부)

```
Document 생성 직후 (null)
        │ DraftService.triggerDraftGeneration() 호출 시
        ▼
    [DRAFT] ──── Gemini 호출 중
        │ 성공 또는 실패 시
        ▼
    [IDLE]
```

### TeamSpace.status (미완성)

```
팀스페이스 생성 시
        │
        ▼
  [CREATING] ──── 이후 CREATED로 전환되지 않음 (버그)
```

---

## 4. 전체 로직 흐름

### 4-1. 팀스페이스 생성 (`POST /api/teamspaces`)

```
1. 팀스페이스 이름 유효성 검사 (null/blank 체크)
2. User 조회
3. TeamSpace 엔티티 생성 (status = CREATING)
4. TeamSpace 저장
5. 생성자를 OWNER 역할로 TeamspaceMember 저장
6. request.getDocumentTypes()가 null이 아니면:
   a. 각 DocumentType에 대해 Document 엔티티 생성
      - id: UUID
      - title: DocumentType.name() (예: "API_SPEC")  ← enum 이름 그대로 사용
      - status: null (DocumentAiStatus 미설정)
   b. documentRepository.saveAll(documents)
   c. 각 Document에 대해 draftService.triggerDraftGeneration(doc.getId()) 호출
7. TeamSpaceCreateResponse 반환 (status = "CREATING")
```

**주목**: `request.getIdea()` 필드가 존재하지만 **어디서도 읽지 않는다.** 사용자가 입력한 아이디어는 초안 생성에 전혀 사용되지 않는다.

### 4-2. 초안 생성 트리거 (`DraftService.triggerDraftGeneration`)

```
1. documentId로 Document 조회
2. 해당 문서에 PENDING 상태 Draft가 이미 있으면 return (중복 방지)
3. document.setStatus(DRAFT)
4. Draft 엔티티 생성 (status = PENDING)
5. Draft 저장
6. TransactionSynchronization.afterCommit()에 draftAsyncExecutor.generateDraftAsync(draftId) 등록
```

`afterCommit()`을 사용하는 이유: `TeamSpaceService.create()`의 외부 트랜잭션이 커밋된 후 비동기 호출이 실행되도록 하여, 비동기 스레드에서 Draft를 DB에서 조회할 수 있도록 보장한다.

### 4-3. Gemini 초안 생성 (`DraftAsyncExecutor.generateDraftAsync`)

```
1. draftId로 Draft 조회
2. draft.getDocument()로 Document 접근
3. buildPrompt(document.getType())으로 프롬프트 생성:
   - 문서 타입에 따라 typeInstruction 결정
   - [ROLE]: IT 스타트업 기획 전문가
   - [TASK]: 해당 타입의 초안 마크다운 작성 요청
   - [INSTRUCTION]: [TODO] 형태, 섹션 제목 구체적으로, 전체 길이 1000자
4. callGeminiApi(prompt) 호출:
   - responseMimeType: application/json
   - responseSchema: {"content": STRING} (마크다운을 JSON 래핑)
   - maxOutputTokens: 4096
5. 성공 시:
   - draft.setContent(content)
   - draft.setStatus(DONE)
   - document.setStatus(IDLE)
   - publishEvent(documentId, "draft:ready", {content})
6. 실패 시:
   - draft.setErrorMessage("초안 생성에 실패했습니다.")
   - draft.setStatus(FAILED)
   - document.setStatus(IDLE)
   - publishEvent(documentId, "draft:error", {errorMessage})
```

### 4-4. 문서 타입별 프롬프트 내용

| DocumentType | typeInstruction |
|---|---|
| `IDEA` | 서비스 아이디어 기획서 (핵심 가치, 타깃 사용자, 차별점 포함) |
| `PLAN` | 프로젝트 계획서 (목표, 주요 기능, 개발 단계, 예상 일정 포함) |
| `USER_SCENARIO` | 유저 시나리오 문서 (주요 사용자 유형, Use Case 3~5개 포함) |
| `API_SPEC` | REST API 명세서 (주요 엔드포인트, 요청/응답 형식 포함) |
| `ERD` | ERD 설명 문서 (주요 엔티티, 핵심 필드, 관계 포함) |

### 4-5. WebSocket 이벤트

`DraftAsyncExecutor`는 `FeedbackEventPublisher.publishToDocument()`를 사용해 **문서 WebSocket** 채널(`/ws/documents/{docId}`)에 이벤트를 발행한다.

| 이벤트 타입 | 발행 시점 | 페이로드 |
|---|---|---|
| `draft:ready` | 초안 생성 완료 | `type`, `documentId`, `content` |
| `draft:error` | 초안 생성 실패 | `type`, `documentId`, `errorMessage` |

### 4-6. WebSocket 연결 시 초안 상태 복원

문서 WebSocket 연결 시 `doc:init` 이벤트에 `activeDraft` 필드가 포함된다.

```java
draftRepository.findByDocumentId(docId)
    .map(d -> new ActiveDraftInfo(d.getId(), d.getStatus(), d.getContent()))
    .orElse(null);
```

상태 구분 없이 Draft가 존재하면 무조건 포함한다. `content`는 DONE일 때만 채워진다.

---

## 5. 핵심 컴포넌트 분석

### 5-1. 병렬 Gemini 호출 구조

`TeamSpaceService.create()`에서 문서 타입 N개를 선택하면 N번의 `afterCommit()` 콜백이 등록된다. 외부 트랜잭션 커밋 후 N개의 `@Async` 호출이 동시에 실행된다.

```
트랜잭션 커밋
    │
    ├─ afterCommit #1 → @Async → Gemini 호출 (IDEA 문서)
    ├─ afterCommit #2 → @Async → Gemini 호출 (PLAN 문서)
    ├─ afterCommit #3 → @Async → Gemini 호출 (USER_SCENARIO 문서)
    ├─ afterCommit #4 → @Async → Gemini 호출 (API_SPEC 문서)
    └─ afterCommit #5 → @Async → Gemini 호출 (ERD 문서)
```

Spring `@EnableAsync`에 별도 `ThreadPoolTaskExecutor` Bean이 없으므로 Spring 기본 실행기를 사용한다. 기본 실행기는 스레드 수 제한이 없어 5개 문서 선택 시 5개 스레드가 동시에 Gemini를 호출한다.

### 5-2. Gemini JSON 래핑 방식

초안 내용은 마크다운이지만, Gemini 응답 스키마에서 JSON `content` 필드로 감싸서 받는다.

```json
{
  "content": "# 서비스 아이디어\n\n## 핵심 가치\n[TODO] ..."
}
```

이를 통해 `ObjectMapper.readValue(json, Map.class)`로 파싱한 뒤 `result.get("content")`로 마크다운을 추출한다. 마크다운 내 따옴표, 백슬래시 등이 JSON 이스케이핑된다.

### 5-3. 중복 생성 방지 로직

```java
if (draftRepository.existsByDocumentIdAndStatus(documentId, DraftStatus.PENDING)) {
    return;
}
```

PENDING 상태인 Draft가 이미 있으면 새 초안 생성을 건너뛴다. DONE이나 FAILED 상태는 체크하지 않으므로, DONE/FAILED인 문서에 대해 `triggerDraftGeneration`을 다시 호출하면 두 번째 Draft 행이 생성된다.

---

## 6. 현재 문제점

### 6-1. [심각] `idea` 필드가 완전히 무시됨

**현상**: `TeamSpaceCreateRequest`에 `idea` 필드가 선언되어 있고, DTO 주석에도 "AI 초안 생성용"으로 명시되어 있다. 그러나 `TeamSpaceService.create()`에서 `request.getIdea()`를 단 한 번도 호출하지 않는다. `DraftAsyncExecutor.buildPrompt()`에도 아이디어가 전달되지 않는다.

```java
// TeamSpaceCreateRequest
private String idea; // 입력 아이디어 (AI 초안 생성용) ← 선언은 되어 있지만

// TeamSpaceService.create()
// ... request.getName() 사용
// ... request.getDocumentTypes() 사용
// request.getIdea()는 어디서도 호출되지 않음
```

**영향**: 사용자가 "배달앱을 만들고 싶다"는 아이디어를 입력해도 Gemini는 이 맥락 없이 완전히 범용 초안을 생성한다.

### 6-2. [심각] TeamSpaceStatus가 CREATING에서 CREATED로 절대 전환되지 않음

**현상**: `TeamSpaceService.create()`에서 `TeamSpaceStatus.CREATING`으로 설정한 후, 어떤 코드도 이를 `CREATED`로 변경하지 않는다.

```java
// TeamspaceWebSocketHandler.java:211
public void publishTeamspaceReady(String teamspaceId) {
    // CREATED 이벤트를 발행하는 메서드가 있지만
}
// → 이 메서드를 호출하는 코드가 전혀 없음
```

`publishTeamspaceReady()`는 구현되어 있지만 `DraftAsyncExecutor`에서 호출되지 않는다. 모든 팀스페이스의 상태는 생성 이후 영원히 `CREATING`으로 남아있다.

**영향**: 프론트엔드가 `status == CREATED`를 체크해 팀스페이스 초기화 완료를 판단한다면 영원히 로딩 상태에 빠진다.

### 6-3. [심각] 초안 완료 이벤트가 잘못된 WebSocket 채널로 발행됨

**현상**: `DraftAsyncExecutor`는 `FeedbackEventPublisher.publishToDocument()`를 사용하여 **문서 WebSocket** 채널(`/ws/documents/{docId}`)로 이벤트를 발행한다. 그러나 팀스페이스 생성 직후 사용자는 아직 개별 문서를 열지 않았으므로 문서 WebSocket에 연결되어 있지 않다.

```
초안 생성 완료
    │
    ▼
FeedbackEventPublisher.publishToDocument(docId, "draft:ready")
    │
    ▼
DocumentWebSocketHandler.docSessions.get(docId)
    │
    ▼
해당 docId에 연결된 세션 목록... 팀스페이스 생성 직후엔 보통 비어있음
    │
    ▼
이벤트 유실
```

사용자가 개별 문서를 열어야 `draft:ready` 이벤트를 수신할 수 있는데, 그때는 `doc:init`에서 `activeDraft`를 통해 이미 Draft 상태를 알 수 있으므로 WebSocket 이벤트가 실용적이지 않다.

팀스페이스 WebSocket(`/ws/teamspace/{teamspaceId}`)에 `publishTeamspaceReady`를 호출해야 사용자가 즉시 완료 알림을 받을 수 있다.

### 6-4. [중간] `callGeminiApi`에 null/빈 응답 검증 없음

**현상**: `DraftAsyncExecutor.callGeminiApi()`는 응답 검증을 전혀 하지 않는다.

```java
// DraftAsyncExecutor (현재)
List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content"); // NPE 가능
List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
String json = (String) parts.get(0).get("text"); // 첫 번째 part가 thought일 경우 오작동
```

```java
// GeminiService (피드백 쪽 - 올바른 구현)
if (candidates == null || candidates.isEmpty()) {
    throw new IllegalStateException("Gemini가 빈 응답을 반환");
}
String resultJson = parts.stream()
        .filter(p -> !Boolean.TRUE.equals(p.get("thought"))) // thought 필터링
        .map(p -> (String) p.get("text"))
        .filter(t -> t != null && !t.isBlank())
        .findFirst()
        .orElseThrow(...);
```

`DraftAsyncExecutor`는 `GeminiService`와 동일한 Gemini API를 호출하지만 방어 코드 수준이 훨씬 낮다. Gemini가 safety filter로 응답을 차단하거나 빈 candidates를 반환하면 NPE가 발생한다.

### 6-5. [중간] N개 문서에 N개 동시 Gemini 호출, 스로틀링 없음

5개 문서 타입 선택 시 5개 Gemini API 호출이 동시에 실행된다. Gemini API의 분당 요청 수(RPM) 제한에 걸릴 수 있다. `@EnableAsync`에 커스텀 `ThreadPoolTaskExecutor`가 없어서 스레드 수도 제어되지 않는다.

```java
// TeamSpaceService.create()
documents.forEach(doc -> draftService.triggerDraftGeneration(doc.getId()));
// → 트랜잭션 커밋 후 N개 @Async 동시 실행
```

### 6-6. [중간] 읽기 타임아웃 미설정

`DraftAsyncExecutor`의 `RestClient`도 `GeminiService`와 동일하게 `connectTimeout(10s)`만 설정되어 있다. 읽기 타임아웃이 없어 Gemini가 응답을 시작했지만 매우 느리게 전송하면 스레드가 무기한 블록된다.

### 6-7. [중간] `RestClient`가 Spring Bean이 아닌 필드 초기화

```java
private final RestClient restClient = RestClient.builder()
        .requestFactory(new JdkClientHttpRequestFactory(...))
        .build();
```

`DraftAsyncExecutor`도 `GeminiService`와 같은 패턴으로 `RestClient`를 필드 레벨에서 초기화한다. Spring 생명주기 밖에 있으며, Bean 수준 설정(baseUrl, timeout 등)을 통합하기 어렵다.

### 6-8. [낮음] 문서 제목이 enum 이름 그대로 설정됨

```java
// TeamSpaceService.create()
Document.create(UUID.randomUUID().toString(), saved, type, type.name())
// type.name() → "IDEA", "PLAN", "USER_SCENARIO", "API_SPEC", "ERD"
```

사용자가 보게 되는 문서 제목이 "API_SPEC", "USER_SCENARIO" 같은 enum 상수 이름이다.

### 6-9. [낮음] `DraftRepository.findByDocumentId`와 `@OneToOne` 유니크 제약 불일치

`Draft.document`에는 `@OneToOne`이 선언되어 있지만, `@JoinColumn(unique=true)`가 없어서 DB 레벨 유니크 제약이 없다. 동시 요청으로 같은 `document_id`를 가진 Draft 행이 두 개 생기면 `findByDocumentId`(`Optional<Draft>` 반환)가 `IncorrectResultSizeDataAccessException`을 던진다.

### 6-10. [낮음] `doc:init`의 `activeDraft`가 상태와 무관하게 모든 Draft를 반환

```java
// DocumentWebSocketHandler.sendDocInit()
draftRepository.findByDocumentId(docId)
    .map(d -> new ActiveDraftInfo(d.getId(), d.getStatus(), d.getContent()))
    .orElse(null);
```

FAILED 상태의 Draft도 `activeDraft`로 클라이언트에 전달된다. `content`가 null인 상태로 전달되므로, 프론트엔드가 `status == "FAILED"`를 명시적으로 처리하지 않으면 null 접근 오류가 발생할 수 있다.

### 6-11. [낮음] 실제 에러 메시지 유실

```java
} catch (Exception e) {
    log.error("[DRAFT] 생성 실패 draftId={}", draftId, e);
    draft.setErrorMessage("초안 생성에 실패했습니다."); // 하드코딩된 메시지
    ...
}
```

실제 예외 정보(예: API 키 오류, 토큰 초과 등)는 로그에만 남고 DB에는 저장되지 않는다. 운영 중 `draft.errorMessage`만 보고는 실패 원인을 알 수 없다.

---

## 7. 개선 방향

### 7-1. `idea` 필드를 `buildPrompt()`에 전달

`TeamSpaceCreateRequest.idea`를 `DraftService` → `Draft` 엔티티 → `DraftAsyncExecutor.buildPrompt()`까지 전달하는 경로를 추가한다.

```java
// Draft 엔티티에 필드 추가
@Column(columnDefinition = "TEXT")
private String ideaContext;

// DraftAsyncExecutor.buildPrompt() 수정
private String buildPrompt(DocumentType type, String ideaContext, String teamspaceName) {
    String ideaSection = (ideaContext != null && !ideaContext.isBlank())
            ? "[IDEA]\n" + ideaContext + "\n\n"
            : "";
    // ...
}
```

### 7-2. 모든 초안 완료 시 TeamSpaceStatus → CREATED 전환 + `teamspace:ready` 이벤트 발행

각 `DraftAsyncExecutor.generateDraftAsync()` 완료 후 해당 팀스페이스의 모든 문서 Draft가 종료 상태(DONE or FAILED)인지 확인하고, 마지막 Draft가 완료되면 TeamSpace status를 `CREATED`로 업데이트하고 `publishTeamspaceReady()`를 호출한다.

```java
// DraftAsyncExecutor.generateDraftAsync() 마지막에 추가
checkAndFinalizeTeamspace(document.getTeamspace().getTeamspaceId());

private void checkAndFinalizeTeamspace(String teamspaceId) {
    List<Document> docs = documentRepository.findByTeamspaceId(teamspaceId);
    boolean allDone = docs.stream().allMatch(d ->
        d.getStatus() == DocumentAiStatus.IDLE
    );
    if (allDone) {
        teamSpaceRepository.findById(teamspaceId).ifPresent(ts -> {
            ts.setStatus(TeamSpaceStatus.CREATED);
        });
        teamspaceWebSocketHandler.publishTeamspaceReady(teamspaceId);
    }
}
```

단, 동시성 문제가 있으므로 락 처리 또는 `@Transactional`과 함께 사용해야 한다.

### 7-3. 초안 완료 이벤트를 팀스페이스 WebSocket으로 발행

각 Draft 완료(`draft:ready`)도 문서 WebSocket이 아닌 팀스페이스 WebSocket으로 알려야 사용자가 즉시 피드백을 받을 수 있다.

```java
// DraftAsyncExecutor에서
teamspaceWebSocketHandler.publishDraftReady(teamspaceId, documentId, content);
```

이를 위해 `FeedbackEventPublisher`와 유사하게 팀스페이스용 이벤트 발행 인터페이스를 분리하면 좋다.

### 7-4. `callGeminiApi` 방어 코드 강화

`GeminiService.callGeminiApi()`의 수준으로 통일한다.

```java
List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
if (candidates == null || candidates.isEmpty()) {
    throw new IllegalStateException("Gemini 빈 응답");
}
Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
if (content == null) throw new IllegalStateException("content 없음");
List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
String json = parts.stream()
        .filter(p -> !Boolean.TRUE.equals(p.get("thought"))) // thought 필터링
        .map(p -> (String) p.get("text"))
        .filter(t -> t != null && !t.isBlank())
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("text 파트 없음"));
```

### 7-5. 순차 생성 또는 스로틀링으로 Gemini 부하 제어

N개 동시 Gemini 호출 대신 순차 실행하거나, 커스텀 `ThreadPoolTaskExecutor`로 동시성을 제한한다.

```java
// AsyncConfig.java 추가
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "geminiTaskExecutor")
    public Executor geminiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("gemini-");
        executor.initialize();
        return executor;
    }
}

// DraftAsyncExecutor에 적용
@Async("geminiTaskExecutor")
@Transactional
public void generateDraftAsync(String draftId) { ... }
```

### 7-6. `DraftRepository`에 유니크 제약 추가

```java
@OneToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "document_id", nullable = false, unique = true) // unique = true 추가
private Document document;
```

또는 DB 마이그레이션으로 `ALTER TABLE drafts ADD UNIQUE (document_id)`.

### 7-7. 문서 제목을 사람이 읽기 좋은 한글로 설정

```java
private static String toDefaultTitle(DocumentType type) {
    return switch (type) {
        case IDEA          -> "서비스 아이디어";
        case PLAN          -> "프로젝트 계획서";
        case USER_SCENARIO -> "유저 시나리오";
        case API_SPEC      -> "API 명세서";
        case ERD           -> "ERD 설계";
    };
}
// Document.create(..., toDefaultTitle(type)) 으로 변경
```

### 7-8. `activeDraft` 응답에서 FAILED 필터링

```java
// DocumentWebSocketHandler.sendDocInit()
draftRepository.findByDocumentId(docId)
    .filter(d -> d.getStatus() != DraftStatus.FAILED) // FAILED는 제외
    .map(d -> new ActiveDraftInfo(d.getId(), d.getStatus(), d.getContent()))
    .orElse(null);
```

또는 FAILED도 포함하되 프론트엔드 인터페이스에 명확히 명시한다.

---

## 8. 초안 퀄리티 향상 방안

### 8-1. 아이디어 컨텍스트 활용 (가장 임팩트 큰 개선)

현재 프롬프트는 문서 타입 설명만 있고 프로젝트 맥락이 전혀 없다. `idea` 필드를 전달하면 즉시 품질이 향상된다.

```
현재:
[TASK] 새 팀 프로젝트의 REST API 명세서 초안을 마크다운으로 작성해줘.

개선 후:
[PROJECT IDEA]
배달앱 서비스를 개발하려고 합니다. 음식점과 소비자를 연결하는 플랫폼입니다.

[TASK] 위 아이디어를 기반으로 REST API 명세서 초안을 마크다운으로 작성해줘.
```

### 8-2. IDEA 문서를 먼저 생성하고, 다른 문서 생성 시 IDEA 내용을 컨텍스트로 사용

AI 피드백 시스템(`FeedbackService`)이 IDEA 문서를 찾아 컨텍스트로 활용하는 것처럼, 초안 생성도 동일한 패턴을 따를 수 있다.

```
생성 순서:
1. IDEA 문서 초안 생성 (아이디어 입력 기반)
2. IDEA 초안 완료 후 → PLAN, USER_SCENARIO, API_SPEC, ERD 초안 생성
   - 각 문서 프롬프트에 [IDEA DOCUMENT] 섹션 추가
```

이를 위해 현재의 병렬 생성 구조를 IDEA 우선 + 나머지 순차/병렬의 2단계로 변경해야 한다.

### 8-3. 더 구체적인 문서 타입별 프롬프트

현재 프롬프트의 `typeInstruction`이 너무 짧다. 문서 타입별로 실제 작성해야 할 섹션과 내용을 명시하면 더 유용한 초안을 생성한다.

```java
case API_SPEC -> """
    REST API 명세서를 작성해줘. 반드시 아래 섹션을 포함해:
    - 인증 방식 (JWT, OAuth2 등)
    - 공통 응답 형식 (성공/에러)
    - 주요 도메인별 엔드포인트 (최소 3개 도메인)
      - GET/POST/PUT/DELETE 메서드
      - 요청 파라미터 및 Body
      - 응답 예시
    - 에러 코드 목록
    """;
```

### 8-4. `systemInstruction` 분리

Gemini API의 `systemInstruction` 필드를 활용해 불변적인 역할 정의를 분리한다.

```java
Map<String, Object> requestBody = Map.of(
    "systemInstruction", Map.of("parts", List.of(
        Map.of("text", "너는 IT 스타트업 기획 전문가야. 사용자가 실제로 사용할 수 있는 구체적인 기획 문서 초안을 마크다운으로 작성한다.")
    )),
    "contents", List.of(Map.of("parts", List.of(Map.of("text", userPrompt)))),
    ...
);
```

### 8-5. [TODO] 마커 일관성 및 구체성

현재 프롬프트에 "실제로 채워야 할 내용은 [TODO] 형태로 표시"라고 지시하지만, 프롬프트에 예시가 없어 Gemini가 [TODO]를 어떻게 쓸지 일관성이 없다.

```
[INSTRUCTION] 추가:
- [TODO] 마커 사용 예시:
  - 좋음: [TODO: 핵심 타깃 연령대 및 직군 명시]
  - 나쁨: [TODO] (무슨 내용을 써야 하는지 모름)
- 이미 알 수 있는 내용(문서 제목, 기본 구조)은 [TODO] 없이 직접 작성
```

### 8-6. thinking 활성화

현재 `thinkingBudget`이 `callGeminiApi`에 설정되어 있지 않아 Gemini 기본값을 따른다. 피드백 시스템과 마찬가지로 `thinkingBudget: 0`이 암묵적으로 적용될 수 있다. 초안 퀄리티를 높이려면 thinking을 허용하는 것이 효과적이다.

```java
"thinkingConfig", Map.of("thinkingBudget", 1024) // 또는 -1 (자동)
```

단, 응답 시간이 늘어날 수 있으므로 `maxOutputTokens: 4096`도 함께 재검토 필요하다.

### 8-7. 팀스페이스 이름을 프롬프트에 포함

현재 프롬프트에 팀스페이스 이름이 포함되지 않는다. `document.getTeamspace().getName()`을 전달하면 프로젝트 맥락이 생긴다.

```
[PROJECT NAME] 맛집 배달 서비스
[TASK] 위 프로젝트의 REST API 명세서 초안을 마크다운으로 작성해줘.
```

---

## 참고: 현재 구현과 AI 피드백 시스템의 차이점

| 항목 | AI 초안 (Draft) | AI 피드백 (Feedback) |
|------|----------------|----------------------|
| 트리거 | 팀스페이스 생성 시 자동 | 사용자가 명시적으로 요청 |
| 프롬프트 구성 | 문서 타입만 사용 | 실제 문서 내용 + 아이디어 컨텍스트 |
| Gemini 응답 방어 코드 | 미흡 (null 체크 없음) | 충분 (null/empty/thought 필터링) |
| WebSocket 이벤트 채널 | 문서 채널 (부적절) | 문서 채널 (적절 — 문서 수정 중 발생) |
| 팀스페이스 상태 갱신 | 없음 (버그) | 해당 없음 |
| 실행 모델 | N개 병렬 (문서 수만큼) | 요청당 1개 |
| 재시도 | 없음 | 없음 (공통 문제) |
