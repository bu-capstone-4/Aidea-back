# 초안 생성 시스템 개선 계획

## 배경

[docs/ai-draft.md](ai-draft.md)에서 발견된 3가지 심각 이슈를 해결한다.

| 이슈 | 내용 | 방향 |
|------|------|------|
| **6-1** | `idea` 필드가 완전히 무시됨 | 프롬프트에 아이디어 컨텍스트 전달 |
| **6-2** | `TeamSpaceStatus`가 영원히 CREATING | `TeamSpaceStatus` 자체를 제거, 문서별 `DocumentAiStatus`로 대체 |
| **6-3** | `draft:ready` 이벤트가 문서 WebSocket으로 발행됨 | 팀스페이스 WebSocket 채널로 변경 |

---

## 작업 개요: 정확한 실행 순서

> **먼저 읽어야 할 섹션이다.** 이 작업에는 DB 변경이 2가지 있는데, 두 변경이 서로 반대 방향의 순서 제약을 가진다. 순서를 잘못 지키면 서비스가 다운되거나 앱이 시작 자체를 거부한다.

---

### 왜 순서가 복잡한가 — 두 마이그레이션의 제약 조건

| 마이그레이션 | 내용 | 제약 조건 |
|---|---|---|
| **V1** | `drafts.idea_context` 컬럼 **추가** | 코드 배포 **이전 또는 동시**에 실행해야 한다 |
| **V2** | `teamspace.status` 컬럼 **삭제** | 코드 배포 **이후**에 실행해야 한다 |

이 두 제약이 왜 충돌처럼 보이는지, 각각 이유를 설명한다.

---

#### V1 제약: 컬럼 추가는 코드 배포 이전 또는 동시에

이번 작업에서 `application.yml`의 `ddl-auto`를 `update → validate`로 바꾼다. `validate` 모드에서는 앱이 시작할 때 JPA가 엔티티 클래스와 실제 DB 테이블을 비교해서 불일치하면 **앱 시작을 거부**한다.

`Draft` 엔티티에 `ideaContext` 필드가 추가된 코드를 배포하면 JPA는 "DB에 `idea_context` 컬럼이 있어야 한다"고 기대한다. 만약 이 시점에 컬럼이 DB에 없으면:

```
Caused by: org.hibernate.tool.schema.spi.SchemaManagementException:
Schema-validation: missing column [idea_context] in table [drafts]
```

앱이 시작하지 않는다. 즉, **코드가 먼저 배포되면 DB에 컬럼이 없어서 앱이 죽는다.**

따라서 V1은 코드 배포 이전에 실행되어 있거나, 코드 배포와 동시에 (앱 시작 시 Flyway가 자동으로) 실행되어야 한다.

---

#### V2 제약: 컬럼 삭제는 반드시 코드 배포 이후에

`TeamSpace` 엔티티에서 `status` 필드를 삭제하는 코드가 배포되기 전에 `teamspace.status` 컬럼을 DROP하면, 아직 운영 중인 **기존 코드**가 해당 컬럼을 읽으려다 런타임 에러를 낸다.

```
java.sql.SQLSyntaxErrorException: Unknown column 'ts.status' in 'field list'
```

모든 팀스페이스 조회 API가 터진다. 즉, **컬럼 삭제는 반드시 그 컬럼을 참조하는 코드가 완전히 내려간 뒤에** 실행해야 한다.

---

### Flyway가 이 문제를 어떻게 해결하는가

Flyway는 `*.sql` 파일을 **앱이 HTTP 요청을 받기 전**, 즉 Spring 컨텍스트 초기화 단계에서 실행한다. 순서는 다음과 같다:

```
컨테이너 시작
    ↓
Flyway 실행 (V1 → V2 순서로 SQL 자동 실행)
    ↓
JPA 스키마 검증 (validate)
    ↓
HTTP 요청 수락 시작
```

V1과 V2 파일이 모두 같은 배포에 포함되어 있다면, 앱이 요청을 받기 전에 컬럼 추가(V1)와 컬럼 삭제(V2)가 순서대로 완료된다. 그 뒤에 JPA가 "엔티티와 DB가 일치하는지" 검증하는데, 이 시점에는 이미 마이그레이션이 끝난 상태이므로 통과한다.

**즉, V1과 V2를 같은 PR/배포에 넣으면 자동으로 올바른 순서가 보장된다.**

---

### "코드 수정 다 하고 나서 DB 마이그레이션 하면 안 되는가?"

안 된다. 정확히 말하면, **V1은 안 되고 V2는 된다.**

| 시나리오 | V1 (add idea_context) | V2 (drop status) |
|---|---|---|
| 코드 배포 먼저, 마이그레이션 나중 | ❌ 앱 시작 불가 (`validate` 모드에서 컬럼 없음 에러) | ✅ 가능 (코드에서 이미 status 참조 제거됨) |
| 마이그레이션 먼저, 코드 배포 나중 | ✅ 가능 (기존 코드는 새 컬럼 무시) | ❌ 서비스 장애 (기존 코드가 status 읽다 에러) |
| 코드 배포와 동시 (Flyway) | ✅ 가능 (앱 시작 전에 Flyway가 컬럼 추가 후 검증) | ✅ 가능 (코드 배포와 함께 자동 실행) |

두 조건을 동시에 만족하는 방법은 **Flyway를 이용해 코드와 마이그레이션을 함께 배포하는 것**뿐이다.

---

### 확정된 작업 순서 (전체 흐름)

아래 순서를 반드시 지킨다. 화살표 방향이 곧 시간 순서다.

```
┌─────────────────────────────────────────────────────────────┐
│  STEP 1. Flyway 도입 준비 (로컬 작업, 배포 없음)              │
│                                                             │
│  1-1. build.gradle에 flyway-core, flyway-mysql 의존성 추가  │
│  1-2. application.yml 수정                                  │
│       ddl-auto: update  →  ddl-auto: validate               │
│       flyway.enabled: true                                  │
│       flyway.baseline-on-migrate: true                      │
│  1-3. src/main/resources/db/migration/ 디렉터리 생성         │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  STEP 2. 마이그레이션 SQL 파일 작성 (로컬 작업)               │
│                                                             │
│  2-1. V1__add_idea_context_to_drafts.sql 작성               │
│  2-2. V2__drop_status_from_teamspace.sql 작성               │
│                                                             │
│  ※ 이 단계에서 로컬 DB로 반드시 검증 (아래 참고)             │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  STEP 3. 코드 변경 (로컬 작업)                               │
│                                                             │
│  3-1. TeamSpaceStatus.java 삭제                             │
│  3-2. Draft.java — ideaContext 필드 추가                    │
│  3-3. TeamSpace.java — status 필드 제거                     │
│  3-4. 서비스/DTO/WebSocket 파일 수정 (섹션 3 전체)           │
│  3-5. TeamspaceEventPublisher 인터페이스 신규 생성           │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  STEP 4. 로컬 전체 검증                                      │
│                                                             │
│  4-1. 로컬 MySQL 초기화 또는 별도 DB로 처음부터 테스트        │
│       → ./gradlew bootRun 실행                              │
│       → 앱 시작 시 콘솔에서 Flyway 로그 확인                 │
│          "Migrating schema to version 1"  ← V1 실행됨       │
│          "Migrating schema to version 2"  ← V2 실행됨       │
│          "Successfully applied 2 migrations"                │
│       → flyway_schema_history 테이블 확인                   │
│          SELECT * FROM flyway_schema_history;               │
│          (2개 행, success=1이어야 함)                        │
│  4-2. POST /api/teamspaces 호출 → idea 필드 포함 확인        │
│  4-3. 팀스페이스 WebSocket에서 draft:ready 수신 확인         │
│  4-4. GET /api/teamspaces/{id} 응답에 status 없고            │
│        documents[].aiStatus 있는지 확인                     │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  STEP 5. PR 생성 및 운영 배포                                │
│                                                             │
│  5-1. PR에 포함되는 것:                                      │
│       - build.gradle (flyway 의존성)                        │
│       - application.yml (ddl-auto, flyway 설정)             │
│       - V1, V2 SQL 파일                                     │
│       - 모든 코드 변경 파일                                  │
│  5-2. 배포 시 자동 실행 순서:                                │
│       새 컨테이너 시작                                       │
│       → Flyway V1 실행 (idea_context 컬럼 추가)              │
│       → Flyway V2 실행 (status 컬럼 삭제)                   │
│       → JPA validate 통과                                   │
│       → HTTP 요청 수락                                      │
│       → (Blue-Green) 트래픽 전환                            │
│       → 기존 컨테이너 종료                                   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  STEP 6. 운영 배포 후 확인                                   │
│                                                             │
│  6-1. 운영 DB에서 flyway_schema_history 확인                 │
│       SELECT version, description, success                  │
│       FROM flyway_schema_history ORDER BY installed_rank;   │
│       → v=1 success=1, v=2 success=1 이어야 함              │
│  6-2. 기능 정상 동작 확인 (STEP 4와 동일한 시나리오)          │
└─────────────────────────────────────────────────────────────┘
```

---

### 로컬 검증 시 주의사항

로컬 DB는 이미 `status` 컬럼이 있는 상태다. Flyway를 처음 실행하면 `baseline-on-migrate: true` 덕분에 현재 상태를 V0으로 등록하고 V1, V2를 순서대로 실행한다.

만약 로컬에서 테스트하다가 뭔가 꼬였다면, 아래 SQL로 Flyway 기록만 초기화한 뒤 재시도할 수 있다:

```sql
-- 로컬 개발 환경에서만 사용. 운영 DB에서 절대 실행 금지
DROP TABLE IF EXISTS flyway_schema_history;
-- 그 다음 직접 컬럼도 원래 상태로 되돌린 뒤 앱 재시작
```

단, V2가 이미 실행되어 `status` 컬럼이 삭제됐다면 수동으로 다시 추가해야 한다:

```sql
ALTER TABLE teamspace ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'CREATING';
ALTER TABLE drafts DROP COLUMN IF EXISTS idea_context;
DROP TABLE IF EXISTS flyway_schema_history;
```

그 뒤 앱을 재시작하면 Flyway가 다시 V1, V2를 처음부터 실행한다.

---

## 실행 가이드 (세션별 Claude Code 프롬프트)

> 이 섹션이 실제 작업 진행표다. 위쪽 섹션(설계 결정, DB 계획, 코드 변경 계획)은 참고 자료이고, 실제로 따라가야 할 순서는 여기다.
> 각 세션은 Claude Code에 프롬프트를 입력하는 단위다. **세션 하나 완료 → 내가 직접 확인 → 다음 세션** 순서로 진행한다.

---

### 진행 상황

- [ ] Session 1 — Flyway 셋업
- [ ] Session 1 완료 후 로컬 검증
- [ ] Session 2 — 엔티티 & 인터페이스
- [ ] Session 3 — 서비스 레이어
- [ ] Session 4 — DTO
- [ ] Session 5 — WebSocket
- [ ] 전체 빌드 검증 (`./gradlew build -x test`)
- [ ] 로컬 통합 테스트 (API 호출)
- [ ] main push → 배포 완료

---

### Session 1 — Flyway 셋업

**Claude Code에 입력할 프롬프트:**

```
docs/plan-draft-improvement.md 의 섹션 2를 보고 Flyway를 이 프로젝트에 도입해줘.

해야 할 작업:
1. build.gradle 에 flyway-core, flyway-mysql 의존성 추가
2. src/main/resources/application.yml 에서 ddl-auto를 validate로 바꾸고 flyway 설정 블록 추가. baseline-on-migrate: true 포함
3. src/main/resources/db/migration/ 디렉터리 생성 후 V1__add_idea_context_to_drafts.sql, V2__drop_status_from_teamspace.sql 파일 작성. SQL 내용은 문서 섹션 2-3 참고

dev 프로필 application.yml도 있으면 같이 확인해서 ddl-auto 설정이 중복되지 않게 해줘.

모든 작업이 완료되면 마지막에 아래 형식으로 출력해줘:

---
✅ Session 1 완료! 다음 내용을 확인해주세요:
1. docker compose -f docker-compose.dev.yml up -d 로 MySQL 시작
2. ./gradlew bootRun --args='--spring.profiles.active=dev' 실행
3. 터미널 로그에서 아래 문구 확인:
   "Migrating schema to version 1 - add idea context to drafts"
   "Migrating schema to version 2 - drop status from teamspace"
   "Successfully applied 2 migrations"
4. 위 로그가 보이면 Ctrl+C로 앱 내리고 Session 2 진행
5. 안 보이면 에러 메시지를 Claude Code에 붙여넣고 물어보기
---
```

---

### [내가 직접 확인] Session 1 완료 후

```bash
docker compose -f docker-compose.dev.yml up -d
./gradlew bootRun --args='--spring.profiles.active=dev'
```

기대 로그:
```
Migrating schema to version 1 - add idea context to drafts
Migrating schema to version 2 - drop status from teamspace
Successfully applied 2 migrations
```

확인되면 `Ctrl+C` → 진행 상황에서 `Session 1` 체크 후 Session 2 진행.

---

### Session 2 — 엔티티 & 인터페이스

**Claude Code에 입력할 프롬프트:**

```
docs/plan-draft-improvement.md 섹션 3을 보고 아래 작업을 해줘.

1. domain/teamspace/entity/TeamSpaceStatus.java 삭제

2. domain/draft/entity/Draft.java 수정
   - ideaContext 필드 추가 (@Column name="idea_context", columnDefinition="MEDIUMTEXT")
   - Draft.create() 팩토리 메서드에 ideaContext 파라미터 추가

3. domain/teamspace/entity/TeamSpace.java 수정
   - status 필드 및 TeamSpaceStatus import 제거

4. domain/teamspace/service/TeamspaceEventPublisher.java 인터페이스 신규 생성
   - publishDraftReady(String teamspaceId, String documentId, String draftId, String content)
   - publishDraftError(String teamspaceId, String documentId)

변경 후 ./gradlew compileJava 로 컴파일 에러 확인하고 고쳐줘.
TeamSpaceStatus를 참조하는 다른 파일들도 에러가 나면 같이 수정해줘.

모든 작업이 완료되면 마지막에 아래 형식으로 출력해줘:

---
✅ Session 2 완료! 다음 내용을 확인해주세요:
1. ./gradlew compileJava 결과가 BUILD SUCCESSFUL 인지 확인
2. TeamSpaceStatus.java 파일이 삭제됐는지 확인
3. Draft.java에 ideaContext 필드가 추가됐는지 확인
4. TeamspaceEventPublisher.java 인터페이스가 새로 생성됐는지 확인
5. 이상 없으면 Session 3 진행
---
```

---

### Session 3 — 서비스 레이어

**Claude Code에 입력할 프롬프트:**

```
docs/plan-draft-improvement.md 섹션 3을 보고 서비스 레이어 3개 파일을 수정해줘.

1. domain/draft/service/DraftService.java
   - triggerDraftGeneration() 시그니처를 (String documentId, String ideaContext, String teamspaceName) 로 변경
   - Draft.create() 호출 시 ideaContext 전달
   - generateDraftAsync 호출 시 teamspaceName도 전달

2. domain/draft/service/DraftAsyncExecutor.java
   - FeedbackEventPublisher 의존성 제거, TeamspaceEventPublisher 주입
   - generateDraftAsync() 시그니처에 teamspaceId, teamspaceName 파라미터 추가
   - buildPrompt()에 ideaContext, teamspaceName 파라미터 추가해서 [PROJECT NAME], [IDEA] 섹션 포함
   - 완료/실패 이벤트를 teamspaceEventPublisher로 발행 (문서 채널 아닌 팀스페이스 채널)
   - callGeminiApi() 응답 파싱에 null 방어 코드 추가

3. domain/teamspace/service/TeamSpaceService.java
   - TeamSpaceStatus import 및 사용 코드 전체 제거
   - triggerDraftGeneration() 호출 시 idea와 teamspaceName 전달
   - 응답 빌더에서 .status(...) 제거

변경 후 ./gradlew compileJava 로 에러 없는지 확인하고 고쳐줘.

모든 작업이 완료되면 마지막에 아래 형식으로 출력해줘:

---
✅ Session 3 완료! 다음 내용을 확인해주세요:
1. ./gradlew compileJava 결과가 BUILD SUCCESSFUL 인지 확인
2. DraftAsyncExecutor.java에 FeedbackEventPublisher 참조가 남아있지 않은지 확인
3. buildPrompt() 메서드에 [PROJECT NAME], [IDEA] 섹션이 포함됐는지 확인
4. 이상 없으면 Session 4 진행
---
```

---

### Session 4 — DTO

**Claude Code에 입력할 프롬프트:**

```
docs/plan-draft-improvement.md 섹션 3을 보고 아래 DTO 파일들을 수정해줘.

제거 작업 (status 필드 삭제):
- domain/teamspace/dto/TeamSpaceCreateResponse.java — status 필드 제거
- domain/teamspace/dto/TeamSpaceListResponse.java — 내부 TeamSpaceSummary에서 status 제거
- domain/teamspace/dto/TeamSpaceSummary.java — status 필드 제거
- domain/teamspace/dto/TeamSpaceUpdateRequest.java — status 필드 제거
- domain/teamspace/dto/TeamSpaceDetailResponse.java — 팀스페이스 레벨 status 필드 제거

추가 작업 (aiStatus 추가):
- domain/teamspace/dto/TeamSpaceDetailResponse.java 내부 DocumentSummary에 aiStatus 필드 추가
- domain/documents/dto/DocumentSummary.java — aiStatus 필드 추가, from() 팩토리 메서드에서 document.getStatus()를 aiStatus에 매핑

변경 후 ./gradlew compileJava 에러 없는지 확인하고 고쳐줘.

모든 작업이 완료되면 마지막에 아래 형식으로 출력해줘:

---
✅ Session 4 완료! 다음 내용을 확인해주세요:
1. ./gradlew compileJava 결과가 BUILD SUCCESSFUL 인지 확인
2. 수정된 DTO 파일들에서 status 필드가 완전히 사라졌는지 확인
3. TeamSpaceDetailResponse.DocumentSummary와 DocumentSummary에 aiStatus 필드가 추가됐는지 확인
4. 이상 없으면 Session 5 진행
---
```

---

### Session 5 — WebSocket

**Claude Code에 입력할 프롬프트:**

```
docs/plan-draft-improvement.md 섹션 3-3의 (K) 항목을 보고 TeamspaceWebSocketHandler.java를 수정해줘.

1. 클래스 선언에 TeamspaceEventPublisher 인터페이스 구현 추가
2. sendTeamspaceInit() 에서 status 관련 코드 제거
3. publishDraftReady(), publishDraftError() 메서드 구현
4. 내부 publishEvent() 헬퍼 메서드 추가 (팀스페이스 ID로 해당 세션들에게 JSON 브로드캐스트)
5. 더 이상 쓰지 않는 publishTeamspaceReady() 메서드 삭제

변경 후 ./gradlew compileJava 에러 없는지 확인하고 고쳐줘.

모든 작업이 완료되면 마지막에 아래 형식으로 출력해줘:

---
✅ Session 5 완료! 모든 코드 작업이 끝났습니다. 이제 전체 빌드와 통합 테스트를 진행해주세요:

[전체 빌드 검증]
1. ./gradlew build -x test 실행
2. BUILD SUCCESSFUL 확인
3. 에러 나오면 에러 메시지를 Claude Code에 붙여넣고 "아래 빌드 에러를 고쳐줘." 요청

[로컬 통합 테스트]
4. docker compose -f docker-compose.dev.yml up -d
5. ./gradlew bootRun --args='--spring.profiles.active=dev'
6. 아래 시나리오 순서대로 확인:
   ① POST /api/teamspaces — idea 필드 포함, 응답에 status 필드 없는지 확인
   ② 팀스페이스 WebSocket (ws://localhost:8080/ws/teamspaces/{id}) 연결 유지 → draft:ready 이벤트 수신 확인
   ③ GET /api/teamspaces/{id} — documents[].aiStatus 있고 완료 후 "IDLE" 인지 확인
   ④ 앱 로그에서 [PROJECT NAME], [IDEA] 섹션이 Gemini 프롬프트에 포함됐는지 확인

[WebSocket 클라이언트가 없을 때]
wscat 이 없으면 Claude Code에 아래를 요청:
"localhost:8080 에 연결해서 POST /api/teamspaces 로 팀스페이스를 만들고,
팀스페이스 WebSocket에 연결해서 draft:ready 이벤트가 오는지 확인하는
간단한 테스트 스크립트를 작성해줘."

[모든 테스트 통과 후]
git add .
git commit -m "feat: Flyway 도입 및 초안 생성 시스템 개선"
git push origin main
→ GitHub Actions 배포 완료 후 Discord 성공 알림 확인
---
```

---

## 목차

1. [설계 결정](#1-설계-결정)
2. [DB 마이그레이션 계획](#2-db-마이그레이션-계획)
3. [코드 변경 계획](#3-코드-변경-계획)
4. [작업 순서 및 체크리스트](#4-작업-순서-및-체크리스트)
5. [위험 요소](#5-위험-요소)

---

## 1. 설계 결정

### 1-1. TeamSpaceStatus 제거

`TeamSpaceStatus(CREATING/CREATED)`를 완전히 삭제하고 문서별 `DocumentAiStatus(IDLE/DRAFT/FEEDBACK_IN_PROGRESS)`로 초기화 상태를 표현한다.

**변경 전**: 팀스페이스 전체 상태를 하나의 `status` 컬럼으로 표현 → 실제로는 업데이트되지 않아 항상 CREATING

**변경 후**: 각 문서가 `aiStatus = DRAFT`이면 해당 문서의 초안이 생성 중임을 나타낸다. 프론트엔드는 `GET /api/teamspaces/{id}` 응답의 문서 목록에서 `aiStatus == DRAFT`인 문서가 있으면 "초기화 중"으로 표시한다.

### 1-2. 초안 완료 이벤트 채널 변경

초안 생성 결과(`draft:ready`, `draft:error`)를 **문서 WebSocket** 채널 대신 **팀스페이스 WebSocket** 채널로 발행한다.

팀스페이스 WebSocket의 기존 이벤트 형식 `{ "event": "...", "data": { ... } }`을 따른다.

```json
// draft:ready
{
  "event": "draft:ready",
  "data": {
    "documentId": "...",
    "draftId": "...",
    "content": "..."
  }
}

// draft:error
{
  "event": "draft:error",
  "data": {
    "documentId": "..."
  }
}
```

`TeamspaceEventPublisher` 인터페이스를 새로 만들고 `TeamspaceWebSocketHandler`가 구현한다. `FeedbackEventPublisher` 패턴과 동일한 구조다.

### 1-3. 아이디어 컨텍스트 흐름

```
POST /api/teamspaces { idea: "배달앱..." }
    │
    ▼
TeamSpaceService.create(request)
    │  request.getIdea(), saved.getName() 전달
    ▼
DraftService.triggerDraftGeneration(docId, idea, teamspaceName)
    │  Draft 엔티티에 ideaContext 저장
    ▼
DraftAsyncExecutor.buildPrompt(type, ideaContext, teamspaceName)
    │
    ▼
Gemini 프롬프트에 [PROJECT NAME], [IDEA] 섹션 포함
```

`Draft` 엔티티에 `idea_context` 컬럼을 추가해 아이디어를 저장한다. 비동기 실행 시 Draft에서 꺼내서 프롬프트에 사용한다.

### 1-4. 응답 DTO에 문서 aiStatus 포함

`TeamSpaceDetailResponse.DocumentSummary`와 `DocumentSummary`에 `aiStatus` 필드를 추가한다. 프론트엔드가 REST API 응답만으로 각 문서의 초안 생성 상태를 알 수 있다.

---

## 2. DB 마이그레이션 계획

### 2-1. 변경 내용

| 작업 | 테이블 | 컬럼 | 내용 |
|------|--------|------|------|
| **ADD** | `drafts` | `idea_context` | 아이디어 컨텍스트 TEXT 컬럼 추가 |
| **DROP** | `teamspace` | `status` | `CREATING`/`CREATED` 값 컬럼 삭제 |

---

### 2-2. Flyway 도입 배경 및 개념

#### Flyway가 무엇인가

지금 프로젝트는 `spring.jpa.hibernate.ddl-auto: update`를 사용하고 있다. 이 설정은 JPA가 엔티티 클래스를 보고 DB 스키마를 **자동으로 추측해서 바꿔준다**. 개발 초기에는 편하지만 아래 문제가 있다:

- 컬럼 **삭제**는 절대 자동으로 안 된다 (`status` 컬럼 제거 불가)
- 운영 DB와 개발 DB가 언제 달라졌는지 기록이 없다
- 여러 명이 개발할 때 "내 로컬엔 되는데 왜 운영은 안 되지?" 문제 발생

**Flyway**는 SQL 파일에 버전 번호를 붙여서 관리하는 라이브러리다. 동작 방식:

1. 앱이 시작될 때 Flyway가 DB의 `flyway_schema_history` 테이블을 확인한다
2. "아직 실행 안 된" SQL 파일만 순서대로 자동 실행한다
3. 한 번 실행된 파일은 절대 다시 실행되지 않는다 (체크섬으로 검증)
4. 실행 기록이 DB에 남아서 "지금 이 DB는 V3까지 적용됐다"는 걸 알 수 있다

#### 파일 이름 규칙

Flyway는 파일 이름으로 버전을 파악한다. 반드시 아래 형식을 지켜야 한다:

```
V{버전}__{설명}.sql
 ↑        ↑↑
 대문자V   언더스코어 2개 (중요!)
```

예시:
```
V1__add_idea_context_to_drafts.sql      ← 먼저 실행
V2__drop_status_from_teamspace.sql      ← 그 다음 실행
V3__create_new_table.sql                ← 그 다음...
```

버전 번호는 숫자만 쓰면 되고, 설명 부분은 영문 소문자 + 언더스코어로 쓰면 가독성이 좋다.

---

### 2-3. Flyway 도입 절차 (기존 DB가 이미 있는 경우)

> **핵심 문제**: 지금 운영 DB에는 이미 테이블이 존재한다. 만약 Flyway를 그냥 켜면 "V1 파일을 실행해야 하는데 테이블이 이미 있어서 오류!" 가 발생한다.
> **해결책**: `baseline-on-migrate` 옵션으로 "현재 상태를 V0으로 선언"한 뒤, V1부터 새 변경사항을 적용한다.

#### Step 1 — `build.gradle`에 Flyway 의존성 추가

```groovy
// build.gradle > dependencies 블록에 추가
implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-mysql'  // MySQL 전용 드라이버 지원
```

Spring Boot 3.x는 Flyway를 자동으로 인식하므로 별도 설정 없이 의존성만 추가하면 앱 시작 시 자동 실행된다.

#### Step 2 — `application.yml` 설정 변경

`ddl-auto`를 `update`에서 `validate`로 바꾼다. 이제 스키마 변경은 JPA가 아니라 Flyway가 담당하고, JPA는 "엔티티와 DB가 일치하는지 검증"만 한다.

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate   # update → validate 로 변경

  flyway:
    enabled: true
    baseline-on-migrate: true   # 기존 DB가 있을 때 필수 (V0으로 현재 상태 등록)
    baseline-version: 0         # 현재 상태를 "버전 0"으로 마킹
    locations: classpath:db/migration   # SQL 파일 위치 (기본값이므로 생략 가능)
```

`baseline-on-migrate: true`의 의미:
- `flyway_schema_history` 테이블이 없으면 → 현재 DB 상태를 V0으로 등록하고 V1부터 실행
- `flyway_schema_history` 테이블이 이미 있으면 → 기록을 보고 실행 안 된 것만 실행 (일반 동작)

이 옵션은 **최초 Flyway 도입 시 한 번만** 유효하다. 이후에는 기록 테이블이 생성되므로 자동으로 일반 모드로 동작한다.

#### Step 3 — 마이그레이션 SQL 파일 작성

파일 위치: `src/main/resources/db/migration/`

이 디렉터리가 없으면 직접 만든다:

```
src/
  main/
    resources/
      db/
        migration/
          V1__add_idea_context_to_drafts.sql
          V2__drop_status_from_teamspace.sql
```

**`V1__add_idea_context_to_drafts.sql`** (배포 전 또는 배포와 동시에 실행)

```sql
-- drafts 테이블에 idea_context 컬럼 추가
-- NULL 허용이므로 기존 행에 영향 없음
ALTER TABLE drafts
    ADD COLUMN idea_context MEDIUMTEXT NULL
    AFTER document_id;
```

**`V2__drop_status_from_teamspace.sql`** (신규 코드 배포 완료 후 실행)

```sql
-- teamspace 테이블에서 status 컬럼 삭제
-- 반드시 신규 코드(TeamSpaceStatus 엔티티 제거)가 배포된 이후에 실행해야 한다
ALTER TABLE teamspace
    DROP COLUMN status;
```

> **중요**: V1과 V2를 동시에 커밋하면 앱 시작 시 둘 다 실행된다. V2는 `teamspace.status` 컬럼을 참조하는 **기존 코드가 먼저 내려간 뒤**에 실행되어야 하므로, V2 파일은 코드 변경과 **같은 PR에 포함**시켜 배포한다. 아래 롤아웃 전략 참고.

---

### 2-4. 롤아웃 전략 (안전한 배포 순서)

Flyway를 쓰면 "파일을 넣으면 앱 시작 시 자동 실행"이므로, 파일을 **언제 포함시키느냐**가 곧 실행 시점이 된다.

```
[PR 1] Flyway 도입 + V1 파일만 포함
    └─ build.gradle: flyway 의존성 추가
    └─ application.yml: ddl-auto → validate, flyway 설정 추가
    └─ V1__add_idea_context_to_drafts.sql
    └─ 배포 → 앱 시작 시 V1 자동 실행 (idea_context 컬럼 추가됨)

[PR 2] 코드 변경 + V2 파일 포함
    └─ TeamSpaceStatus 엔티티 삭제
    └─ 모든 코드 변경 (3-1 ~ 3-3 항목)
    └─ V2__drop_status_from_teamspace.sql
    └─ 배포 → 앱 시작 시 V2 자동 실행 (status 컬럼 삭제됨)
```

PR을 두 개로 나누는 이유:
- V1(컬럼 추가)은 기존 코드에 영향 없으므로 먼저 안전하게 적용 가능
- V2(컬럼 삭제)는 코드에서 해당 컬럼 참조가 완전히 제거된 후에만 안전

한 번에 하고 싶다면 PR을 합쳐도 되지만, Blue-Green 배포 중 트래픽 전환 전에 기존 컨테이너가 살아있는 순간 V2가 실행되면 기존 컨테이너가 오류를 낼 수 있다.

---

### 2-5. 자주 하는 실수 및 주의사항

| 실수 | 결과 | 대처 |
|------|------|------|
| V1 파일을 수정하고 재배포 | Flyway가 체크섬 불일치 오류로 앱 시작 실패 | 한 번 배포된 파일은 절대 수정하지 말 것. 수정이 필요하면 V3 파일을 새로 만들어서 보정 SQL 작성 |
| 언더스코어를 하나만 씀 (`V1_add...`) | Flyway가 파일을 인식하지 못하고 실행 안 됨 | 파일 이름에 언더스코어 **두 개** 확인 (`V1__add...`) |
| `ddl-auto: update` 그대로 두고 Flyway 추가 | JPA와 Flyway 둘 다 스키마를 건드려서 충돌 가능 | 반드시 `validate` 또는 `none`으로 변경 |
| `baseline-on-migrate` 없이 기존 DB에 Flyway 첫 적용 | V1 SQL이 이미 존재하는 테이블을 다시 만들려다 오류 | `application.yml`에 `baseline-on-migrate: true` 추가 |

---

## 3. 코드 변경 계획

### 3-1. 새로 생성할 파일

#### `domain/teamspace/service/TeamspaceEventPublisher.java`

```java
package com.aidea.aidea.domain.teamspace.service;

public interface TeamspaceEventPublisher {
    void publishDraftReady(String teamspaceId, String documentId, String draftId, String content);
    void publishDraftError(String teamspaceId, String documentId);
}
```

`FeedbackEventPublisher`와 동일한 역할이다. `TeamspaceWebSocketHandler`가 구현한다. `DraftAsyncExecutor`는 이 인터페이스에만 의존해 WebSocket 인프라와 분리된다.

---

### 3-2. 삭제할 파일

- `domain/teamspace/entity/TeamSpaceStatus.java` — 삭제

---

### 3-3. 수정할 파일 목록

#### (A) `draft/entity/Draft.java`

`ideaContext` 필드 추가.

```java
// 추가
@Column(name = "idea_context", columnDefinition = "MEDIUMTEXT")
private String ideaContext;
```

`Draft.create()` 팩토리 메서드에 `ideaContext` 파라미터 추가.

```java
public static Draft create(String id, Document document, String ideaContext) {
    Draft draft = new Draft();
    draft.id = id;
    draft.document = document;
    draft.status = DraftStatus.PENDING;
    draft.createdAt = LocalDateTime.now();
    draft.ideaContext = ideaContext;
    return draft;
}
```

---

#### (B) `draft/service/DraftService.java`

`triggerDraftGeneration()` 시그니처에 `ideaContext`, `teamspaceName` 파라미터 추가.

```java
// 변경 전
public void triggerDraftGeneration(String documentId)

// 변경 후
public void triggerDraftGeneration(String documentId, String ideaContext, String teamspaceName)
```

내부에서 `Draft.create(id, document, ideaContext)` 호출 변경, `generateDraftAsync`에 `teamspaceName`도 전달.

```java
Draft draft = Draft.create(UUID.randomUUID().toString(), document, ideaContext);
draftRepository.save(draft);

String draftId = draft.getId();
String tsId = document.getTeamspace().getTeamspaceId();
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        draftAsyncExecutor.generateDraftAsync(draftId, tsId, teamspaceName);
    }
});
```

---

#### (C) `draft/service/DraftAsyncExecutor.java`

**주요 변경 3가지:**

1. `FeedbackEventPublisher` 의존성 제거, `TeamspaceEventPublisher` 주입
2. `generateDraftAsync()` 시그니처에 `teamspaceId`, `teamspaceName` 파라미터 추가
3. `buildPrompt()`에 `ideaContext`, `teamspaceName` 전달
4. 완료/실패 이벤트를 `teamspaceEventPublisher`로 발행
5. `callGeminiApi()`에 null/empty 방어 코드 추가 (GeminiService 수준으로)

```java
// 변경 전
private final FeedbackEventPublisher eventPublisher;

// 변경 후
private final TeamspaceEventPublisher teamspaceEventPublisher;

// generateDraftAsync 시그니처 변경
@Async
@Transactional
public void generateDraftAsync(String draftId, String teamspaceId, String teamspaceName) {
    Draft draft = ...;
    Document document = draft.getDocument();

    try {
        String prompt = buildPrompt(document.getType(), draft.getIdeaContext(), teamspaceName);
        String content = callGeminiApi(prompt);

        draft.setContent(content);
        draft.setStatus(DraftStatus.DONE);
        document.setStatus(DocumentAiStatus.IDLE);

        // 팀스페이스 채널로 발행
        teamspaceEventPublisher.publishDraftReady(teamspaceId, document.getId(), draft.getId(), content);

    } catch (Exception e) {
        draft.setStatus(DraftStatus.FAILED);
        draft.setErrorMessage(e.getMessage()); // 실제 에러 메시지 저장
        document.setStatus(DocumentAiStatus.IDLE);

        teamspaceEventPublisher.publishDraftError(teamspaceId, document.getId());
    }
}
```

`buildPrompt()` 변경: `ideaContext`, `teamspaceName` 추가.

```java
private String buildPrompt(DocumentType type, String ideaContext, String teamspaceName) {
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
```

`callGeminiApi()` 방어 코드 강화 (GeminiService 수준으로):

```java
private String callGeminiApi(String prompt) throws Exception {
    // ... 요청 코드 동일 ...

    List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
    if (candidates == null || candidates.isEmpty()) {
        throw new IllegalStateException("Gemini 빈 응답");
    }

    Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
    if (content == null) throw new IllegalStateException("Gemini content 필드 없음");

    List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
    if (parts == null || parts.isEmpty()) throw new IllegalStateException("Gemini parts 필드 없음");

    String json = parts.stream()
            .filter(p -> !Boolean.TRUE.equals(p.get("thought")))  // thought 파트 필터링
            .map(p -> (String) p.get("text"))
            .filter(t -> t != null && !t.isBlank())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Gemini text 파트 없음"));

    Map<String, String> result = objectMapper.readValue(json, Map.class);
    return result.get("content");
}
```

---

#### (D) `teamspace/service/TeamSpaceService.java`

**삭제**: `TeamSpaceStatus` import 및 사용 코드 전체 제거

**수정**: `draftService.triggerDraftGeneration()` 호출 시 `idea`와 `teamspaceName` 전달

```java
// 변경 전
documents.forEach(doc -> draftService.triggerDraftGeneration(doc.getId()));

// 변경 후
String ideaContext = request.getIdea();
String teamspaceName = saved.getName();
documents.forEach(doc ->
    draftService.triggerDraftGeneration(doc.getId(), ideaContext, teamspaceName)
);
```

**삭제**: `TeamSpace` 생성 시 `.status(TeamSpaceStatus.CREATING)` 제거

**삭제**: `update()` 메서드에서 `request.getStatus()` 처리 블록 제거

**수정**: `TeamSpaceCreateResponse`, `TeamSpaceListResponse` 빌더에서 `.status(...)` 제거

**수정**: `get()` 메서드에서 `TeamSpaceDetailResponse` 빌더에서 `.status(...)` 제거, `DocumentSummary`에 `aiStatus` 추가

```java
// toDocumentSummary() 수정
private TeamSpaceDetailResponse.DocumentSummary toDocumentSummary(Document d) {
    return TeamSpaceDetailResponse.DocumentSummary.builder()
            .id(d.getId())
            .type(d.getType() != null ? d.getType().name() : null)
            .title(d.getTitle())
            .aiStatus(d.getStatus() != null ? d.getStatus().name() : "IDLE")  // 추가
            .updatedAt(d.getUpdatedAt())
            .updatedBy(d.getUpdatedBy() != null ? d.getUpdatedBy().getId().toString() : null)
            .build();
}
```

---

#### (E) `teamspace/entity/TeamSpace.java`

`status` 필드 및 관련 import 제거.

```java
// 삭제
@Enumerated(EnumType.STRING)
@Column(name = "status", nullable = false)
private TeamSpaceStatus status;
```

---

#### (F) `teamspace/dto/TeamSpaceCreateResponse.java`

`status` 필드 제거.

```java
// 삭제
private String status;
```

---

#### (G) `teamspace/dto/TeamSpaceDetailResponse.java`

`status` 필드 제거, `DocumentSummary`에 `aiStatus` 필드 추가.

```java
// 팀스페이스 레벨 status 삭제
// private String status;

// DocumentSummary 내부에 추가
private String aiStatus; // "IDLE" | "DRAFT" | "FEEDBACK_IN_PROGRESS"
```

---

#### (H) `teamspace/dto/TeamSpaceListResponse.java`

`TeamSpaceSummary`에서 `status` 필드 제거.

```java
// 삭제
private String status;
```

---

#### (I) `teamspace/dto/TeamSpaceSummary.java`

`status` 필드 제거.

```java
// 삭제
private String status;
```

---

#### (J) `teamspace/dto/TeamSpaceUpdateRequest.java`

`status` 필드 제거.

```java
// 삭제
private String status;
```

---

#### (K) `teamspace/websocket/TeamspaceWebSocketHandler.java`

**구현**: `TeamspaceEventPublisher` 인터페이스 구현

**수정**: `sendTeamspaceInit()`에서 `status` 제거

**추가**: `publishDraftReady()`, `publishDraftError()` 구현

**제거**: `publishTeamspaceReady()` 메서드 (더 이상 불필요)

```java
// 클래스 선언 변경
public class TeamspaceWebSocketHandler extends TextWebSocketHandler
        implements TeamspaceEventPublisher {  // 추가

// sendTeamspaceInit() 수정
Map<String, Object> teamspaceData = new LinkedHashMap<>();
teamspaceData.put("id", teamSpace.getTeamspaceId());
teamspaceData.put("name", teamSpace.getName());
// teamspaceData.put("status", ...) 삭제

// 추가: TeamspaceEventPublisher 구현
@Override
public void publishDraftReady(String teamspaceId, String documentId, String draftId, String content) {
    Map<String, Object> data = Map.of(
            "documentId", documentId,
            "draftId", draftId,
            "content", content
    );
    publishEvent(teamspaceId, "draft:ready", data);
}

@Override
public void publishDraftError(String teamspaceId, String documentId) {
    publishEvent(teamspaceId, "draft:error", Map.of("documentId", documentId));
}

private void publishEvent(String teamspaceId, String eventType, Map<String, Object> data) {
    Set<WebSocketSession> sessions = teamspaceSessions.getOrDefault(teamspaceId, Collections.emptySet());
    if (sessions.isEmpty()) return;

    try {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", eventType);
        event.put("data", data);
        TextMessage message = new TextMessage(objectMapper.writeValueAsString(event));
        for (WebSocketSession s : sessions) {
            sendSafe(s, message);
        }
    } catch (JsonProcessingException e) {
        log.error("[WS-TS] failed to serialize {} teamspaceId={}", eventType, teamspaceId, e);
    }
}
```

---

#### (L) `documents/dto/DocumentSummary.java`

`aiStatus` 필드 추가.

```java
// 추가
private String aiStatus;

// from() 팩토리 수정
public static DocumentSummary from(Document document) {
    return new DocumentSummary(
            document.getId(),
            document.getType(),
            document.getTitle(),
            document.getUpdatedAt(),
            document.getUpdatedBy() != null ? document.getUpdatedBy().getId().toString() : null,
            document.getStatus() != null ? document.getStatus().name() : "IDLE"  // 추가
    );
}
```

---

## 4. 작업 순서 및 체크리스트

### Phase 1: Flyway 도입 (PR 1 — 코드 변경 없이 먼저 배포)

- [ ] `build.gradle`에 의존성 추가
  ```groovy
  implementation 'org.flywaydb:flyway-core'
  implementation 'org.flywaydb:flyway-mysql'
  ```
- [ ] `application.yml` 수정: `ddl-auto: update` → `ddl-auto: validate`, `flyway` 설정 블록 추가
- [ ] `src/main/resources/db/migration/` 디렉터리 생성
- [ ] `V1__add_idea_context_to_drafts.sql` 파일 작성 및 커밋
- [ ] 로컬에서 앱 시작 후 `flyway_schema_history` 테이블이 생성됐는지 확인
  ```sql
  SELECT * FROM flyway_schema_history;
  -- installed_rank=1, version=1, description=add idea context to drafts, success=1 이어야 함
  ```
- [ ] 배포 (PR 1) → 운영 DB에 `idea_context` 컬럼이 추가됨

### Phase 2: 코드 작업 (PR 2 — V2 마이그레이션 파일 포함)

#### 신규 파일

- [ ] `TeamspaceEventPublisher.java` 인터페이스 생성

#### 삭제 파일

- [ ] `TeamSpaceStatus.java` 삭제

#### 엔티티/도메인 수정

- [ ] `Draft.java` — `ideaContext` 필드 추가, `create()` 시그니처 변경
- [ ] `TeamSpace.java` — `status` 필드 및 import 제거

#### 서비스 수정

- [ ] `DraftService.java` — `triggerDraftGeneration()` 파라미터 추가
- [ ] `DraftAsyncExecutor.java` — `TeamspaceEventPublisher` 주입, 이벤트 채널 변경, 프롬프트 개선, `callGeminiApi()` 방어 코드 추가
- [ ] `TeamSpaceService.java` — `idea` 전달, `status` 관련 코드 전체 제거

#### DTO 수정

- [ ] `TeamSpaceCreateResponse.java` — `status` 필드 제거
- [ ] `TeamSpaceDetailResponse.java` — `status` 필드 제거, `DocumentSummary`에 `aiStatus` 추가
- [ ] `TeamSpaceListResponse.java` — `TeamSpaceSummary.status` 제거
- [ ] `TeamSpaceSummary.java` — `status` 제거
- [ ] `TeamSpaceUpdateRequest.java` — `status` 제거
- [ ] `DocumentSummary.java` — `aiStatus` 추가

#### WebSocket 수정

- [ ] `TeamspaceWebSocketHandler.java` — `TeamspaceEventPublisher` 구현, `publishDraftReady/Error` 추가, `sendTeamspaceInit`에서 `status` 제거, `publishTeamspaceReady()` 삭제

### Phase 3: 테스트

- [ ] 팀스페이스 생성 후 `idea` 필드가 초안 프롬프트에 포함되는지 로그 확인
- [ ] 팀스페이스 WebSocket 연결 후 `draft:ready` 이벤트 수신 확인
- [ ] `GET /api/teamspaces/{id}` 응답에 `status` 필드 없고 `documents[].aiStatus` 있는지 확인
- [ ] `draft:ready` 수신 후 해당 문서의 `aiStatus`가 `IDLE`로 변경되는지 REST API 조회로 확인

### Phase 4: 운영 배포 완료 확인 (PR 2 배포 후)

- [ ] 배포 완료 및 서비스 정상 동작 확인
- [ ] 운영 DB `flyway_schema_history` 테이블에 V2 레코드가 `success=1`로 기록됐는지 확인
  ```sql
  SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
  -- version=2, description=drop status from teamspace, success=1 이어야 함
  ```
- [ ] `teamspace.status` 컬럼이 실제로 없는지 확인 (Flyway가 V2 실행 시 자동으로 DROP됨)
  ```sql
  SHOW COLUMNS FROM teamspace;
  -- status 컬럼이 목록에 없어야 함
  ```

---

## 5. 위험 요소

### 5-1. 프론트엔드 breaking change

`status` 필드가 팀스페이스 REST API 응답과 WebSocket `teamspace:init` 이벤트에서 사라진다. 프론트엔드가 해당 필드를 사용 중이라면 동시에 수정이 필요하다.

**대응**: 프론트엔드와 일정 조율 후 동시 배포.

### 5-2. `TeamSpaceUpdateRequest.status` 제거

외부에서 `PUT /api/teamspaces/{id}` 요청으로 `status`를 변경하던 클라이언트가 있다면 무시된다. 현재 프론트엔드에서 이 API를 통해 status를 변경하는 코드가 있는지 확인 필요.

### 5-3. `DraftAsyncExecutor.generateDraftAsync` 시그니처 변경

`@Async` 메서드의 파라미터가 변경된다. Spring `@Async`는 프록시를 통해 호출되므로 파라미터 변경 자체에 문제는 없으나, 호출부(`DraftService`)도 함께 수정되어야 한다.

### 5-4. `teamspace.status` DROP은 배포 후에

배포 전에 `status` 컬럼을 DROP하면 기존 코드의 `ts.getStatus()` 호출이 런타임 에러를 발생시킨다. 반드시 신규 코드 배포 완료 확인 후 DROP을 실행한다.

### 5-5. 팀스페이스 WebSocket에 연결된 세션이 없을 때

팀스페이스 생성 직후, 클라이언트가 팀스페이스 WebSocket에 연결하기 전에 `draft:ready` 이벤트가 발행되면 이벤트가 유실된다. 이는 기존 `doc:init`의 `activeDraft` 메커니즘으로 보완된다 — 나중에 WebSocket 연결 시 `doc:init` 응답의 `activeDraft.status`로 현재 상태를 알 수 있다.
