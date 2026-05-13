# Yjs 백엔드 — 공통 가이드 (INDEX)

> **AI 에이전트 필독:** 어떤 Phase 작업을 시작하든 이 문서를 **반드시 먼저** 읽어라.
> 이 문서는 전체 프로젝트의 아키텍처, 규칙, 프로토콜을 정의한다. Phase 문서는 구현 세부사항만 담고 있으므로, 이 문서 없이는 일관성을 보장할 수 없다.

---

## 문서 목록 및 네비게이션

| 문서 | 담당 범위 | 전제 조건 |
|---|---|---|
| **이 문서 (index)** | 아키텍처, 규칙, 프로토콜, API 목록 | — |
| [phase1-foundation](./yjs-phase1-foundation.md) | 프로젝트 세팅, DB 엔티티, JWT, 팀스페이스, 권한 | 없음 |
| [phase2-websocket](./yjs-phase2-websocket.md) | WebSocket 설정, UpdateBuffer, Handler | Phase 1 완료 |
| [phase3-batch-merge](./yjs-phase3-batch-merge.md) | 배치 머지 스케줄러, 재시작 폴백 | Phase 2 완료 |
| [phase4-ai-feedback](./yjs-phase4-ai-feedback.md) | Feedback 엔티티, GeminiService, 피드백 이벤트 | Phase 1 + Phase 2 완료 |
| [phase5-polish](./yjs-phase5-polish.md) | 전역 예외처리, 유효성 검사, 테스트 체크리스트 | Phase 1~4 완료 |

**작업 시작 전 체크리스트:**
1. 이 index 문서를 읽었는가?
2. 해당 Phase 문서의 "전제 조건"에 나열된 Phase 문서를 읽었는가?
3. 아래 AI 코딩 규칙을 숙지했는가?

---

## 서비스 개요

yjs는 Notion과 유사한 실시간 협업 기획 문서 서비스다. 핵심 차별점은 다음 세 가지다:
1. **Yjs CRDT 기반 실시간 다중 편집** — 충돌 없는 동시 수정
2. **팀스페이스 권한 관리** — OWNER / MEMBER / VIEWER 역할별 접근 제어
3. **AI 피드백** — 문서 내용을 Gemini에게 검토받고 버전을 선택할 수 있음

---

## 전체 아키텍처

```
Client (React + Yjs)
    │
    ├─── REST API (/api/**)               ← 인증, CRUD, AI 피드백 요청
    │
    └─── WebSocket (/ws/documents/{docId}) ← Yjs 이벤트 송수신 (JSON 텍스트)
             │
     ┌───────┴─────────────┐
     │     Spring Boot      │
     │                      │
     │  WebSocket Handler   │  ← doc:init / doc:update 이벤트 처리
     │        │             │
     │  Update Buffer       │  ← ConcurrentHashMap<docId, List<byte[]>>
     │  (In-Memory)         │    WS Handler와 Scheduler가 공유
     │        │             │
     │  REST Controllers    │  ← 권한 검사, 피드백 상태 관리
     │                      │
     │  AI Service          │  ← Gemini API 비동기 호출
     └───────┬──────────────┘
             │
      ┌──────┴──────┐
      │   MySQL     │
      │             │
      │ documents   │  ← Yjs 스냅샷 영속 저장 (배치 머지 결과)
      │ doc_updates │  ← 실시간 업데이트 영속 로그 (버퍼의 DB 복사본)
      │ feedbacks   │  ← AI 피드백 이력
      └─────────────┘
```

---

## 핵심 설계 원칙

이 원칙들은 모든 Phase에 걸쳐 일관되게 적용된다.

1. **Yjs 바이너리를 서버가 절대 파싱하지 않는다.** 서버는 `byte[]`를 DB에 저장하고 클라이언트에 릴레이할 뿐이다.

2. **업데이트는 인메모리 버퍼와 DB에 이중 저장한다.** WS 핸들러가 업데이트를 수신하면 즉시 인메모리 버퍼에 push하고 DB에도 INSERT한다. 버퍼는 스케줄러가 빠르게 읽기 위한 캐시이며, DB는 재시작 복구를 위한 영속 로그다.

3. **배치 머지는 메모리 버퍼 기준으로 실행한다.** 스케줄러는 DB SELECT 없이 메모리 버퍼에서 업데이트를 가져와 `yjs_snapshot`을 갱신하고, 대응하는 DB rows를 DELETE한다. 스냅샷 UPDATE와 rows DELETE는 반드시 같은 트랜잭션이다.

4. **문서 복원 공식: `yjs_snapshot + 미머지 document_updates`.** 신규 접속 클라이언트는 `doc:init` 이벤트로 이 두 값을 순서대로 받아 `Y.applyUpdate()`로 합산한다.

5. **모든 WebSocket 메시지는 JSON 텍스트 이벤트로 통일한다.** `{ "type": "...", ... }` 포맷, Yjs 바이너리는 Base64 인코딩. 핸들러는 `TextWebSocketHandler`를 상속하고 `handleTextMessage`만 오버라이드한다.

6. **권한 검사는 핸드셰이크 시점과 REST 요청 시점 양쪽에서 수행한다.** WS는 팀스페이스 비소속자 연결 거부, REST는 Service 레이어에서 역할 확인.

7. **AI 피드백은 `@Async` 비동기로 처리하고, 완료 시 WebSocket 이벤트로 푸시한다.** REST 엔드포인트는 즉시 `202 Accepted`를 반환한다.

---

## 기술 스택

| 분류 | 기술 | 비고 |
|---|---|---|
| 언어 | Java 17 | |
| 프레임워크 | Spring Boot 3.x | |
| WebSocket | Spring WebSocket (Raw, not STOMP) | `TextWebSocketHandler` 사용 |
| ORM | Spring Data JPA (Hibernate) | |
| DB | MySQL 8.x | LONGBLOB, JSON 타입 활용 |
| AI | Google Gemini API | `RestClient`로 직접 HTTP 호출 |
| 인증 | JWT (Spring Security) | |
| 비동기 | `@Async` + `@EnableAsync` | AI 피드백용 |
| 빌드 | Gradle (Kotlin DSL) | |

---

## 전체 패키지 구조

```
src/main/java/com/aidea/
├── AideaApplication.java
│
├── global/
│   ├── config/
│   │   ├── SecurityConfig.java          ← JWT 필터, CORS, 권한 설정
│   │   ├── WebSocketConfig.java         ← WebSocket 엔드포인트 등록
│   │   └── AsyncConfig.java             ← @Async 스레드풀 (@EnableAsync)
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java  ← @RestControllerAdvice
│   │   └── CustomException.java         ← 공통 예외 클래스
│   └── security/
│       ├── JwtTokenProvider.java        ← 토큰 생성/검증
│       └── JwtAuthFilter.java           ← OncePerRequestFilter
│
├── user/
│   ├── entity/User.java
│   └── repository/UserRepository.java
│
├── teamspace/
│   ├── controller/TeamspaceController.java
│   ├── service/TeamspaceService.java
│   ├── repository/
│   │   ├── TeamspaceRepository.java
│   │   └── TeamspaceMemberRepository.java
│   └── entity/
│       ├── Teamspace.java
│       ├── TeamspaceMember.java
│       └── MemberRole.java              ← ENUM: OWNER, MEMBER, VIEWER
│
├── document/
│   ├── controller/DocumentController.java
│   ├── service/DocumentService.java
│   ├── repository/
│   │   ├── DocumentRepository.java
│   │   └── DocumentUpdateRepository.java
│   ├── entity/
│   │   ├── Document.java
│   │   ├── DocumentUpdate.java
│   │   └── DocumentType.java            ← ENUM: IDEA, PLAN, USER_SCENARIO, API_SPEC, ERD
│   └── websocket/
│       ├── WebSocketConfig.java
│       ├── DocumentHandshakeInterceptor.java
│       ├── DocumentWebSocketHandler.java ← TextWebSocketHandler 상속
│       └── DocumentUpdateBuffer.java    ← 인메모리 버퍼
│
├── scheduler/
│   └── YjsMergeScheduler.java           ← 배치 머지 스케줄러
│
└── feedback/
    ├── controller/FeedbackController.java
    ├── service/
    │   ├── FeedbackService.java
    │   └── GeminiService.java
    ├── repository/FeedbackRepository.java
    ├── entity/
    │   ├── Feedback.java
    │   └── FeedbackStatus.java          ← ENUM: PENDING, QUESTIONING, ANSWERING, DONE, ACCEPTED
    └── converter/
        ├── QuestionsConverter.java      ← JSON ↔ List<Question>
        └── AnswersConverter.java        ← JSON ↔ List<Answer>
```

---

## WebSocket 메시지 프로토콜 (공통 명세)

Phase 2, Phase 4 모두 이 프로토콜을 기준으로 구현한다.

### Yjs 문서 동기화 이벤트

```json
// 서버 → 클라이언트: 접속 시 초기 문서 상태
{
  "type": "doc:init",
  "updates": [
    "<base64 yjs_snapshot>",   // documents.yjs_snapshot (null이면 생략)
    "<base64 update1>",        // document_updates rows (id ASC 순)
    "<base64 update2>"
  ]
}

// 클라이언트 → 서버: 편집 내용 전송
{
  "type": "doc:update",
  "update": "<base64 Y.encodeStateAsUpdate() 결과>",
  "clientId": "userId|tabId"
}

// 서버 → 클라이언트: 다른 유저의 편집 브로드캐스트
{
  "type": "doc:update",
  "update": "<base64 updateBinary>"
}
```

### AI 피드백 이벤트

```json
// 서버 → 클라이언트: AI가 추가 질문 생성
{
  "type": "feedback:questioning",
  "feedbackId": "...",
  "questions": [
    { "id": "q1", "section": "핵심 기능", "text": "...", "options": ["A", "B"] }
  ]
}

// 서버 → 클라이언트: AI 피드백 준비 완료
{
  "type": "feedback:ready",
  "feedbackId": "...",
  "yjsBinary": "<base64 피드백 Yjs 바이너리>"
}
```

**클라이언트 복원 로직:**
```typescript
// doc:init 수신 시
event.updates.forEach(b64 => Y.applyUpdate(ydoc, Base64.decode(b64)));
```

---

## 전체 REST API 목록

### 문서 관련

| 메서드 | 경로 | 설명 | 최소 권한 |
|---|---|---|---|
| GET | `/api/teamspaces/{tsId}/documents` | 팀스페이스 문서 목록 | VIEWER |
| POST | `/api/teamspaces/{tsId}/documents` | 문서 생성 | MEMBER |
| GET | `/api/documents/{docId}` | 문서 메타데이터 조회 | VIEWER |
| PATCH | `/api/documents/{docId}` | 문서 제목 변경 | MEMBER |
| DELETE | `/api/documents/{docId}` | 문서 삭제 | OWNER |

### 피드백 관련

| 메서드 | 경로 | 설명 | 최소 권한 |
|---|---|---|---|
| POST | `/api/documents/{docId}/feedback` | 피드백 요청 (비동기, 202 반환) | MEMBER |
| GET | `/api/feedbacks/{feedbackId}` | 피드백 상태 폴링 | MEMBER |
| POST | `/api/feedbacks/{feedbackId}/answer` | 질문 답변 제출 | MEMBER |
| POST | `/api/feedbacks/{feedbackId}/accept` | 피드백 버전 수락 | MEMBER |

---

## AI 코딩 규칙

AI 에이전트가 코드를 작성할 때 반드시 따라야 하는 규칙이다. 이 규칙들은 모든 Phase에 동일하게 적용된다.

### 의존성 주입

```java
// 반드시 생성자 주입 + @RequiredArgsConstructor 사용
// @Autowired 필드 주입 절대 금지

@Service
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final TeamspaceMemberRepository teamspaceMemberRepository;
}
```

### JPA 컨벤션

- `byte[]` 컬럼은 반드시 `@Column(columnDefinition = "LONGBLOB")` 명시 (MySQL BLOB 타입 명시)
- JSON 컬럼은 반드시 `@Column(columnDefinition = "JSON")` + `@Convert(converter = ...)` 명시
- 모든 연관관계는 `FetchType.LAZY` 기본
- `@ManyToOne` 외래키는 `@JoinColumn(name = "컬럼명")` 명시
- `@Enumerated(EnumType.STRING)` 반드시 사용 (숫자 인덱스 저장 금지)

### 트랜잭션 경계

- `@Transactional`은 Service 레이어에만 적용
- WebSocket 핸들러 내부에서 직접 `@Transactional` 사용 금지 → `documentService.saveUpdate()`로 위임
- 배치 머지의 `UPDATE documents` + `DELETE document_updates`는 반드시 같은 `@Transactional` 메서드 안에

### 예외 처리

- `RuntimeException`을 직접 던지지 말고 `CustomException` 사용
- Service 레이어에서 권한 없으면 `ForbiddenException` (HTTP 403)
- 리소스 없으면 `NotFoundException` (HTTP 404)
- `GlobalExceptionHandler`에서 일괄 처리 (Phase 5 구현)

### API 응답 형식

```java
// 단일 리소스
{ "id": "...", "title": "...", ... }

// 목록
{ "items": [...], "total": 10 }

// 비동기 작업 시작 (202)
{ "feedbackId": "...", "status": "PENDING" }

// 에러
{ "code": "FORBIDDEN", "message": "팀스페이스 소속이 아닙니다" }
```

### 네이밍 컨벤션

- 클래스: PascalCase (`DocumentService`, `YjsMergeScheduler`)
- 메서드/변수: lowerCamelCase (`saveUpdate`, `drainAndReset`)
- DB 컬럼: snake_case (`yjs_snapshot`, `document_id`)
- REST 경로: kebab-case (`/api/teamspaces/{tsId}/documents`)
- WebSocket 이벤트 타입: `namespace:action` 패턴 (`doc:init`, `feedback:ready`)

### WebSocket 구현 규칙

- `TextWebSocketHandler` 상속 (바이너리 핸들러 사용 금지)
- `docSessions`는 `ConcurrentHashMap<String, Set<WebSocketSession>>` — `HashSet` 절대 금지
- 세션 Set은 `ConcurrentHashMap.newKeySet()`으로 생성
- 브로드캐스트 시 `try-catch IOException`으로 끊긴 세션 무시, 정리는 `afterConnectionClosed`에서
- Yjs 바이너리는 항상 `Base64.getEncoder().encodeToString()` / `Base64.getDecoder().decode()`

---

## 공통 주의사항

### LONGBLOB 설정

MySQL에서 `byte[]` 컬럼은 `@Column(columnDefinition = "LONGBLOB")`으로 명시한다. Hibernate가 기본으로 `TINYBLOB`이나 `BLOB`으로 생성할 수 있으므로, Yjs 바이너리처럼 크기가 가변적인 컬럼은 반드시 `LONGBLOB`을 명시해야 한다.

```yaml
# application.yml — MySQL 드라이버 설정
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:3306/${DB_NAME:root}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 트랜잭션 원자성 (배치 머지)

배치 머지에서 `yjs_snapshot UPDATE`와 `document_updates DELETE`가 다른 트랜잭션이면:
- UPDATE 성공 후 DELETE 실패 → 다음 `doc:init`에서 이미 머지된 업데이트가 다시 전송됨
- 클라이언트가 같은 내용을 두 번 적용하게 되어 문서가 깨짐

반드시 `@Transactional` 메서드 하나에서 두 작업을 처리할 것.
