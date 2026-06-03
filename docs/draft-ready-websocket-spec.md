# draft:ready 이벤트 백엔드 송신 방식 및 프론트 연동 가이드

## 1. 현재 백엔드 송신 방식

### 1.1 이벤트 발생 경로

```
팀스페이스 생성 (POST /api/teamspaces)
  └─ TeamSpaceService.createTeamSpace()
       └─ DraftService.triggerDraftGeneration()  ← 문서별 1회 호출
            └─ Draft 엔티티 저장 (status=PENDING)
            └─ TransactionSynchronization.afterCommit()
                 └─ DraftAsyncExecutor.generateDraftAsync()  ← @Async, 별도 스레드
                      └─ Gemini API 호출
                      └─ 성공 시: TeamspaceEventPublisher.publishDraftReady()
                      └─ 실패 시: TeamspaceEventPublisher.publishDraftError()
```

### 1.2 WebSocket 채널

- **경로**: `ws://{host}/ws/teamspaces/{teamspaceId}`
- **핸들러**: `TeamspaceWebSocketHandler`
- **인증**: `TeamspaceHandshakeInterceptor`가 JWT로 userId, teamspaceId, role을 세션 속성에 주입

### 1.3 draft:ready 페이로드 구조

`TeamspaceWebSocketHandler.publishDraftReady()` (line 168):

```json
{
  "event": "draft:ready",
  "data": {
    "documentId": "c2daffad-b2d2-4891-835a-fb8775512048",
    "draftId": "7f9d7d99-dfb1-4446-925d-93d8509e87b2",
    "content": "## 마크다운 전문 텍스트..."
  }
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `event` | string | 항상 `"draft:ready"` |
| `data.documentId` | string (UUID) | 어떤 문서의 초안인지 |
| `data.draftId` | string (UUID) | 초안 고유 ID |
| `data.content` | string | Gemini가 생성한 마크다운 전문 |

### 1.4 draft:error 페이로드 구조

```json
{
  "event": "draft:error",
  "data": {
    "documentId": "c2daffad-b2d2-4891-835a-fb8775512048"
  }
}
```

`draftId`나 오류 사유는 포함되지 않는다.

---

## 2. 백엔드 관점에서 식별된 문제점

### 2.1 타이밍 문제 (가장 중요)

로그상 `draft:ready` 이벤트 발생 시점과 프론트의 문서 WebSocket 연결 시점이 어긋난다:

```
12:49:43 - 팀스페이스 생성, Gemini 비동기 호출 시작
12:49:45 - 프론트가 문서 WebSocket 연결 (doc:init snapshotPresent=false)
12:50:02 - draft:ready 이벤트 브로드캐스트 (teamspace WebSocket으로)
12:49:47 - 첫 번째 문서 WebSocket 연결 끊김 (status=1000, 정상 종료)
```

프론트는 각 문서 페이지에 진입할 때 **Document WebSocket**(`/ws/documents/{docId}`)을 연결했다가 페이지를 나오면 끊는 패턴으로 동작 중이다. `draft:ready` 이벤트는 **Teamspace WebSocket**(`/ws/teamspaces/{teamspaceId}`)으로만 전송된다.

### 2.2 draft:ready 수신 후 문서에 내용을 쓰는 주체가 없음

현재 백엔드가 하는 일:
1. Gemini 결과를 `drafts` 테이블에 저장 (`draft.content`, `status=DONE`)
2. `draft:ready` 이벤트를 teamspace WebSocket으로 브로드캐스트

현재 백엔드가 하지 않는 일:
- `draft.content`를 Yjs 포맷으로 변환해서 문서에 적용
- `document_updates` 테이블에 Yjs 업데이트 저장
- Document WebSocket 세션들에게 `doc:update` 브로드캐스트

즉, **초안 내용은 DB의 `drafts.content`에만 저장**되어 있고, 문서 편집 공간(Yjs)에는 전혀 반영되지 않는다.

### 2.3 draft:ready의 content 필드 용도 불명확

`draft:ready` 이벤트에 `content` 전문을 실어서 보내고 있다. 이는 두 가지 방식 중 하나를 전제한다:

**방식 A**: 프론트가 `content`를 직접 받아서 Yjs 문서에 삽입 (클라이언트 사이드 적용)

**방식 B**: 프론트가 `draft:ready`를 수신하면 별도 REST API로 초안 내용을 조회 (`GET /api/drafts/{draftId}`)

현재 백엔드는 `content`를 이벤트에 포함해서 보내므로 **방식 A를 의도**한 것으로 보이나, 프론트가 해당 content를 Yjs에 반영하는 처리를 하지 않으면 UI에는 아무것도 표시되지 않는다.

---

## 3. 백엔드 수정 방향

### 옵션 1: 백엔드가 Yjs 문서에 직접 초안 내용을 기록 (권장)

`draft:ready` 이벤트를 보낼 때, 동시에 초안 마크다운을 Yjs binary update로 변환하여 `document_updates`에 저장하고 Document WebSocket 세션에 브로드캐스트한다.

**구현 위치**: `DraftAsyncExecutor.generateDraftAsync()` 성공 블록 내

```java
// 초안 content를 Yjs update로 변환 후 문서에 push
documentService.applyDraftContent(document.getId(), content);
// 그 다음 draft:ready 브로드캐스트
teamspaceEventPublisher.publishDraftReady(teamspaceId, document.getId(), draft.getId(), content);
```

단, 이 방식은 서버에서 Yjs 라이브러리(y-crdt 또는 y-websocket 호환 바이너리)를 생성할 수 있어야 하므로 구현 난이도가 높다.

### 옵션 2: draft:ready 수신 시 프론트가 content를 Yjs에 삽입 (현재 구조 유지, 프론트 수정)

백엔드는 현재 이벤트 구조를 그대로 유지한다. 프론트 측에서 `draft:ready` 이벤트의 `data.content`(마크다운)를 받아 현재 열려 있는 Yjs 문서 인스턴스에 직접 삽입하는 처리를 추가해야 한다.

이 경우 백엔드가 추가로 해야 할 일은 없으나, 현재 **content가 DB에만 저장되고 문서 snapshot/update에는 기록되지 않는** 상태이므로 페이지를 새로 고침하면 내용이 사라진다.

### 옵션 3: 초안을 승인(accept)하는 별도 API 추가 (명시적 흐름)

`draft:ready` 이벤트에는 `content`를 포함하지 않고 `draftId`만 전달한다. 사용자가 "초안 적용" 버튼을 누르면 `POST /api/drafts/{draftId}/apply`를 호출하고, 백엔드가 그 시점에 초안을 문서에 반영한다.

이 방식은 사용자 의도를 확인하는 단계가 있어 UX와 데이터 무결성 면에서 안전하다.

---

## 4. 현재 상태 정리

| 항목 | 상태 |
|------|------|
| Gemini 초안 생성 | 정상 동작 (DB에 저장됨) |
| draft:ready 이벤트 전송 | 정상 동작 (teamspace WS로 전송됨) |
| 초안 내용의 문서 반영 | **미구현** - DB에만 저장, Yjs/문서에 미적용 |
| draft:error 이벤트 전송 | 정상 동작 |
| 오류 원인 (JSON 파싱 실패) | 수정 완료 (`responseMimeType: application/json` 제거) |

**결론**: 이벤트는 정상적으로 프론트에 도달하고 있다. 초안 내용이 문서에 반영되지 않는 이유는 백엔드가 초안 마크다운을 Yjs 문서에 쓰는 처리를 하지 않기 때문이다. 위 옵션 중 하나를 선택해서 구현해야 한다.
