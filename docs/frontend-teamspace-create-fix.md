# 팀스페이스 생성 시 AI 초안 미생성 문제 — 프론트엔드 수정 가이드

## 현재 상황

팀스페이스를 생성해도 AI 초안이 생성되지 않는다. 서버 로그를 분석한 결과, 프론트엔드가 팀스페이스 생성과 문서 생성을 두 번의 API 호출로 분리하고 있으며, 이로 인해 AI 초안 생성 트리거가 전혀 실행되지 않는 것이 원인이다.

### 실제로 발생하는 호출 흐름 (현재 — 잘못된 흐름)

```
1. POST /api/teamspaces
   Request Body: { "name": "팀명", "idea": "아이디어", "documentTypes": null }
   → 팀스페이스만 저장, 문서·초안 없음

2. POST /api/documents (문서 타입마다 별도 호출 × 5)
   Request Body: { "teamspaceId": "ts_...", "type": "IDEA" }
   Request Body: { "teamspaceId": "ts_...", "type": "PLAN" }
   ...
   → 문서만 저장, AI 초안 생성 트리거 없음
```

**결과: `[DRAFT]` 관련 로그가 단 한 줄도 찍히지 않음 — 초안 생성 자체가 시작되지 않음**

---

## 원인

백엔드의 AI 초안 생성 로직은 `POST /api/teamspaces` 엔드포인트 내부에서만 실행된다.
구체적으로, 요청 바디의 `documentTypes` 필드가 `null`이 아닐 때만 문서를 생성하고, 각 문서에 대해 AI 초안 생성을 비동기로 트리거한다.

`POST /api/documents`(문서 단건 추가 생성 API)는 AI 초안 생성 로직을 포함하지 않으며, 팀스페이스 최초 세팅용이 아닌 이후 추가 문서 생성 전용이다.

---

## 수정 방향

팀스페이스 생성 시 **`POST /api/teamspaces` 단일 호출로** 팀스페이스와 문서를 함께 생성해야 한다.
이후 `POST /api/documents`를 별도로 호출하는 로직은 제거해야 한다.

### 올바른 호출 흐름 (수정 후)

```
1. POST /api/teamspaces
   Request Body: {
     "name": "팀명",
     "idea": "아이디어 설명",
     "documentTypes": ["IDEA", "PLAN", "USER_SCENARIO", "API_SPEC", "ERD"]
   }
   → 팀스페이스 저장 + 문서 5개 저장 + AI 초안 생성 비동기 트리거
   → 응답으로 생성된 문서 목록 반환

2. POST /api/documents (별도 호출 제거)
   → 팀스페이스 최초 세팅 시에는 호출하지 않음
```

---

## API 명세

### `POST /api/teamspaces` — 팀스페이스 생성

**Request**

```http
POST /api/teamspaces
Content-Type: application/json
Authorization: Bearer {token}
```

```json
{
  "name": "팀스페이스 이름",
  "idea": "서비스 아이디어 설명 (AI 초안 생성에 사용됨)",
  "documentTypes": ["IDEA", "PLAN", "USER_SCENARIO", "API_SPEC", "ERD"]
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `name` | String | ✅ | 팀스페이스 이름 |
| `idea` | String | ❌ | AI 초안 생성에 사용할 아이디어 설명. 없으면 빈 초안 생성 |
| `documentTypes` | String[] | ❌ | 생성할 문서 타입 목록. `null`이면 문서 생성 및 초안 생성 없음 |

**사용 가능한 `documentTypes` 값**

| 값 | 설명 |
|---|---|
| `IDEA` | 서비스 아이디어 기획서 |
| `PLAN` | 프로젝트 계획서 |
| `USER_SCENARIO` | 유저 시나리오 |
| `API_SPEC` | REST API 명세서 |
| `ERD` | ERD 설명 문서 |

---

**Response** `200 OK`

```json
{
  "success": true,
  "message": null,
  "data": {
    "teamspaceId": "ts_3346cba5-ed85-4ea5-bc23-8711a4fd440d",
    "name": "팀스페이스 이름",
    "createdAt": "2026-06-01T12:36:52",
    "documents": [
      {
        "id": "4853257c-f9f3-480b-8051-1f7daddd21f3",
        "type": "IDEA",
        "title": "IDEA",
        "aiStatus": "DRAFT"
      },
      {
        "id": "85e02edb-b7eb-433c-a38c-2388173bdb84",
        "type": "PLAN",
        "title": "PLAN",
        "aiStatus": "DRAFT"
      }
    ]
  }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `teamspaceId` | String | 생성된 팀스페이스 ID |
| `name` | String | 팀스페이스 이름 |
| `createdAt` | String (ISO 8601) | 생성 일시 |
| `documents` | Array | 함께 생성된 문서 목록. `documentTypes` 미전송 시 빈 배열 |
| `documents[].id` | String | 문서 ID |
| `documents[].type` | String | 문서 타입 |
| `documents[].title` | String | 문서 제목 |
| `documents[].aiStatus` | String | AI 상태. 초안 생성 중이면 `DRAFT`, 완료 후 `IDLE` |

---

## AI 초안 생성 이후 소켓 이벤트

팀스페이스 생성 후 팀스페이스 웹소켓(`ws://.../ws/teamspaces/{teamspaceId}`)에 연결하면, 각 문서의 AI 초안 생성이 완료될 때마다 아래 이벤트를 수신한다.

**초안 생성 완료**
```json
{
  "event": "draft:ready",
  "data": {
    "documentId": "4853257c-f9f3-480b-8051-1f7daddd21f3",
    "draftId": "draft-uuid",
    "content": "# 서비스 아이디어 기획서\n..."
  }
}
```

**초안 생성 실패**
```json
{
  "event": "draft:error",
  "data": {
    "documentId": "4853257c-f9f3-480b-8051-1f7daddd21f3"
  }
}
```

초안 생성은 문서당 최대 수십 초가 소요될 수 있으므로, 팀스페이스 입장 후 소켓 연결을 유지하고 `draft:ready` 이벤트로 초안을 수신해야 한다.

---

## 프론트엔드 수정 체크리스트

- [ ] 팀스페이스 생성 API 호출 시 `documentTypes` 배열을 요청 바디에 포함
- [ ] 팀스페이스 생성 직후 `POST /api/documents`를 별도로 호출하는 로직 제거
- [ ] 생성 응답의 `documents` 배열에서 문서 ID 및 목록 사용 (별도 `GET /api/documents` 불필요)
- [ ] 팀스페이스 소켓 연결 후 `draft:ready` / `draft:error` 이벤트 수신 처리

---

## 백엔드 변경 사항 (이미 적용됨)

| 파일 | 변경 내용 |
|---|---|
| `TeamSpaceCreateResponse` | `documents` 필드 추가 — 생성된 문서 목록 반환 |
| `TeamSpaceService.create()` | 응답에 생성된 문서 목록 포함하도록 수정 |
