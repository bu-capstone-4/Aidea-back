# 백엔드 제안 사항

> 프론트엔드 구현 계획 수립 중 발견된 백엔드 API/WS 스펙 개선 제안.  
> 이 문서는 백엔드 팀에 전달하거나 논의 참고용으로 사용한다.

---

## 제안 1 — 백로그 WS에 온라인 편집자 이벤트 추가

**현황:**  
백로그 WS 스펙에 "온라인 멤버" 이벤트가 없다.  
디자인에는 "2명이 함께 편집 중" 표시와 아바타 스택이 있다.

**현재 대응:**  
임시로 팀스페이스 WS(`/ws/teamspace/{id}`)의 `onlineMembers`에서 전체 온라인 멤버 수를 재활용.  
그러나 팀스페이스 온라인과 "백로그 편집 중" 개념은 다름.

**제안:**  
백로그 WS에서 연결 시 `backlog:init`에 현재 세션 목록 포함:

```json
{
  "type": "backlog:init",
  "config": { ... },
  "epics": [ ... ],
  "stories": [ ... ],
  "onlineEditors": [
    { "id": 1, "name": "강민석", "profileImageUrl": "..." }
  ]
}
```

그리고 다른 유저가 접속/퇴장 시 브로드캐스트:

```json
{
  "type": "backlog:presence",
  "onlineEditors": [{ "id": 1, "name": "강민석", "profileImageUrl": "..." }]
}
```

---

## 제안 2 — 스토리 생성 API에 `status` 필드 지원

**현황:**  
`POST /stories` Request Body에 `status` 필드가 없다. 생성 시 기본값은 `OPEN`.

**문제:**  
보드 뷰에서 "진행 중" 컬럼의 "이슈 추가"를 누르면 해당 status로 생성되어야 함.  
현재 스펙으로는 생성 후 별도로 `PATCH .../status` 호출이 필요 (2-round trip).

**제안:**  
`CreateStoryRequest`에 `status` 필드 추가 (nullable, 기본값 `OPEN`):

```json
{
  "title": "새 스토리",
  "status": "IN_PROGRESS"
}
```

---

## 제안 3 — `UserResponse` 필드 일관성

**현황:**  
기존 인증/팀스페이스 API의 `UserResponse`:

```json
{ "id": 1, "email": "...", "name": "...", "profileImageUrl": "...", "provider": "GITHUB" }
```

백로그 스펙의 `UserResponse`:

```json
{ "id": 1, "name": "...", "githubLogin": "...", "profileImageUrl": "..." }
```

두 객체가 동일한 사용자이지만 필드명과 내용이 다름.

**프론트 대응:**  
`src/types/backlog.ts`에 `BacklogUser` 별도 정의로 처리.

**제안:**  
가능하면 백엔드의 전체 `UserResponse`를 통일하거나,  
백로그에서도 `githubLogin` 대신 기존 `email`/`provider` 포함 여부 확인 필요.  
또는 두 구조의 슈퍼셋으로 통일:

```json
{
  "id": 1,
  "name": "강민석",
  "profileImageUrl": "https://...",
  "githubLogin": "kang-min-seok" // GitHub 연동 시에만 포함
}
```

---

## 제안 4 — `epicEnabled=false` 상태에서 에픽 수정/삭제 허용 여부 명시

**현황:**  
스펙에 의하면 `epicEnabled=false`일 때 에픽 **생성**이 차단 (`POST`).  
그러나 기존에 생성된 에픽의 **수정**(`PUT`)과 **삭제**(`DELETE`)가 차단되는지 명시되지 않음.

**기대 동작:**  
생성은 차단, 수정/삭제는 허용 — 이미 만들어진 에픽은 관리 가능해야 함.

**확인 요청:**  
`epicEnabled=false`인 팀스페이스에서 `PUT /epics/{epicId}`, `DELETE /epics/{epicId}` 동작 확인.

---

## 제안 5 — `story:created` WS 이벤트에 `status` 포함 확인

**현황:**  
`story:created` 이벤트 페이로드가 `StorySummaryResponse` 전체를 포함 (스펙 예시에 `status: "OPEN"` 포함됨). 확인됨.

**추가 확인:**  
`story:updated` 이벤트도 `StorySummaryResponse` 전체를 포함하므로 `status` 변경이 `story:updated`로 브로드캐스트되는지, 아니면 `story:status_changed`만 오는지 확인 필요.

스펙에 따르면 `PUT /stories/{storyId}`는 `story:updated`를, `PATCH /stories/{storyId}/status`는 `story:status_changed`를 트리거함. 일관성 확인 필요.

---

## 제안 6 — AI 초안 생성 엔드포인트 (향후)

**현황:**  
백엔드에서 AI 초안 생성 미구현. 디자인의 "AI로 만들기" 버튼은 현재 일반 "만들기"로 처리.

**향후 구현 시 필요한 API:**

```
POST /api/teamspaces/{teamspaceId}/backlog/generate
Body: { config: BacklogConfigRequest, documentIds?: string[] }
```

또는 WebSocket을 통한 스트리밍 방식.

---

## 제안 7 — 최상위 태스크(Standalone Task) 지원

### 배경

현재 `Task`는 반드시 `Story`의 하위 항목으로만 존재한다.

```
POST /api/teamspaces/{teamspaceId}/stories/{storyId}/tasks
```

그러나 `BacklogConfigResponse.storyEnabled`가 `false`일 수 있고,  
스펙 문서에 "태스크는 기본 이슈 유형이므로 별도 활성화 설정 없이 **항상 사용 가능**"이라 명시되어 있다.  
이는 태스크가 스토리 없이 백로그에 단독으로 존재해야 한다는 설계 의도를 시사한다.

현재는 이슈를 추가할 때 스토리만 생성 가능하고, 태스크를 선택해서 만들 방법이 없다.

### 현재 구조의 문제점

| 상황 | 현재 | 기대 |
| --- | --- | --- |
| 백로그에서 이슈 추가 | 스토리만 생성 가능 | 스토리 / 태스크 선택 후 생성 |
| `storyEnabled=false` 팀스페이스 | 태스크 생성 불가 (부모 스토리 없음) | 태스크를 백로그에 직접 추가 |
| 태스크 위치 | 스토리 내 `position` | 백로그 전체 `position` 필요 |

### 제안: 최상위 태스크 API 추가

#### 7-1. 새 DTO: `BacklogTaskResponse`

최상위 태스크는 스토리 내 태스크(`TaskResponse`)와 달리 백로그 전체에서의 `position`과 `status`가 필요하다.

```json
{
  "id": 10,
  "number": 6,
  "title": "배포 스크립트 작성",
  "status": "OPEN",
  "priority": null,
  "issueType": "BE",
  "sprint": null,
  "assignee": {
    "id": 1,
    "name": "강민석",
    "githubLogin": "kang-min-seok",
    "profileImageUrl": "..."
  },
  "reporter": { "...": "..." },
  "dueDate": null,
  "position": 6000,
  "createdAt": "2026-05-26T10:00:00",
  "updatedAt": "2026-05-26T10:00:00"
}
```

| 필드 | 타입 | Nullable | 설명 |
| --- | --- | --- | --- |
| `id` | `Long` | N | 태스크 식별자 |
| `number` | `Long` | N | 팀스페이스 내 순차 번호 (스토리와 별도 시퀀스 or 통합 시퀀스) |
| `title` | `String` | N | 제목 |
| `status` | `StoryStatus` | N | OPEN / IN\_PROGRESS / DONE / CLOSED |
| `priority` | `Priority` | Y | `priorityEnabled` 시 사용 |
| `issueType` | `IssueType` | Y | `feBeEnabled` 시 사용 |
| `sprint` | `String` | Y | `sprintEnabled` 시 사용 |
| `assignee` | `UserResponse` | Y | 담당자 |
| `reporter` | `UserResponse` | N | 생성자 |
| `dueDate` | `LocalDate` | Y | `dueDateEnabled` 시 사용 |
| `position` | `int` | N | 백로그 내 정렬 순서 |
| `createdAt` | `LocalDateTime` | N | — |
| `updatedAt` | `LocalDateTime` | N | — |

> 스토리 하위 태스크(`TaskResponse`)와 최상위 태스크(`BacklogTaskResponse`)는 별도 엔티티 또는 동일 엔티티에 `storyId nullable`로 구현 가능.

#### 7-2. 새 API 엔드포인트

```
POST   /api/teamspaces/{teamspaceId}/tasks           최상위 태스크 생성
PUT    /api/teamspaces/{teamspaceId}/tasks/{taskId}  수정
PATCH  /api/teamspaces/{teamspaceId}/tasks/{taskId}/status  상태 변경
PATCH  /api/teamspaces/{teamspaceId}/tasks/reorder   순서 변경
DELETE /api/teamspaces/{teamspaceId}/tasks/{taskId}  삭제
```

**`POST /api/teamspaces/{teamspaceId}/tasks` Request Body:**

```json
{
  "title": "배포 스크립트 작성",
  "priority": null,
  "issueType": "BE",
  "sprint": null,
  "assigneeId": null,
  "dueDate": null
}
```

**응답 201** — `GlobalResponse<BacklogTaskResponse>`

#### 7-3. `backlog:init` 업데이트

```json
{
  "type": "backlog:init",
  "config": { "...": "..." },
  "epics": [ "..." ],
  "stories": [ "..." ],
  "tasks": [
    {
      "id": 10,
      "number": 6,
      "title": "배포 스크립트 작성",
      "status": "OPEN",
      "...": "..."
    }
  ]
}
```

#### 7-4. 새 WebSocket 이벤트

| 이벤트 `type` | 트리거 API |
| --- | --- |
| `backlogtask:created` | `POST /tasks` |
| `backlogtask:updated` | `PUT /tasks/{taskId}` |
| `backlogtask:status_changed` | `PATCH /tasks/{taskId}/status` |
| `backlogtask:reordered` | `PATCH /tasks/reorder` |
| `backlogtask:deleted` | `DELETE /tasks/{taskId}` |

> 기존 `task:created` 등은 스토리 하위 태스크 이벤트로 유지. 최상위 태스크는 `backlogtask:` 네임스페이스로 구분.

#### 7-5. 에러 코드 추가

| `code` | HTTP | 설명 |
| --- | --- | --- |
| `BACKLOG_TASK_NOT_FOUND` | 404 | 최상위 태스크를 찾을 수 없음 |

---

## 제안 8 — 에픽(Epic)의 백로그 통합 (GitHub Projects 방식)

### 배경

현재 에픽은 "스토리에 붙이는 레이블/태그" 역할만 한다:
- `EpicResponse`에 `status`, `position`, `assignee` 없음
- 에픽은 전용 관리 모달(`에픽 관리`)에서만 생성 가능
- 에픽이 백로그 목록에 행(row)으로 표시되지 않음
- `backlog:init`의 `stories` 배열에 에픽 미포함

사용자 요구사항: **백로그에서 에픽을 별도 항목으로 추가하고, 스토리/태스크와 함께 나열**할 수 있어야 함.

### 현재 vs 기대

| 기능 | 현재 | 기대 |
| --- | --- | --- |
| 에픽 생성 위치 | 에픽 관리 모달에서만 | 백로그 "+ 이슈 추가"에서도 선택 가능 |
| 에픽 상태 | 없음 | OPEN / IN\_PROGRESS / DONE / CLOSED |
| 에픽 백로그 순서 | 없음 | `position`으로 스토리와 함께 정렬 가능 |
| 에픽 담당자 | 없음 | 스토리처럼 `assignee` 지정 가능 |
| 에픽 번호 | 없음 | `number` 필드 (`EP-001` 형식 표시 가능) |

### 제안: `EpicResponse` 필드 확장

```json
{
  "id": 1,
  "number": 1,
  "name": "인증",
  "color": "#6366f1",
  "description": "사용자 인증 관련 작업",
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "issueType": "FE",
  "assignee": {
    "id": 1,
    "name": "강민석",
    "githubLogin": "kang-min-seok",
    "profileImageUrl": "..."
  },
  "reporter": { "...": "..." },
  "dueDate": "2026-07-01",
  "position": 500,
  "storyCount": 3,
  "completedStoryCount": 1,
  "createdAt": "2026-05-21T10:00:00",
  "updatedAt": "2026-05-26T09:00:00",
  "createdBy": { "...": "..." }
}
```

추가되는 필드:

| 필드 | 타입 | Nullable | 설명 |
| --- | --- | --- | --- |
| `number` | `Long` | N | 팀스페이스 내 순차 번호 |
| `status` | `EpicStatus` | N | OPEN / IN\_PROGRESS / DONE / CLOSED (기본 OPEN) |
| `priority` | `Priority` | Y | `priorityEnabled` 시 사용 |
| `issueType` | `IssueType` | Y | `feBeEnabled` 시 사용 |
| `assignee` | `UserResponse` | Y | 담당자 |
| `reporter` | `UserResponse` | N | 생성자 |
| `dueDate` | `LocalDate` | Y | `dueDateEnabled` 시 사용 |
| `position` | `int` | N | 백로그 내 정렬 순서 |
| `storyCount` | `int` | N | 연결된 스토리 수 |
| `completedStoryCount` | `int` | N | 완료된 스토리 수 |
| `updatedAt` | `LocalDateTime` | N | 수정 시각 |

### 새 API 엔드포인트

```
PATCH  /api/teamspaces/{teamspaceId}/epics/{epicId}/status   에픽 상태 변경
PATCH  /api/teamspaces/{teamspaceId}/epics/reorder           에픽 순서 변경
```

**`POST /api/teamspaces/{teamspaceId}/epics` Request Body 확장:**

```json
{
  "name": "인증",
  "color": "#6366f1",
  "description": "사용자 인증 관련 작업",
  "priority": "HIGH",
  "issueType": "FE",
  "assigneeId": 1,
  "dueDate": "2026-07-01"
}
```

### `backlog:init` 업데이트

에픽을 `stories`, `tasks`와 함께 백로그 전체에서 순서(`position`)를 비교할 수 있도록 `epics` 배열에 확장된 필드 포함:

```json
{
  "type": "backlog:init",
  "config": { "...": "..." },
  "epics": [
    {
      "id": 1,
      "number": 1,
      "name": "인증",
      "color": "#6366f1",
      "status": "IN_PROGRESS",
      "position": 500,
      "storyCount": 3,
      "completedStoryCount": 1,
      "...": "..."
    }
  ],
  "stories": [ "..." ],
  "tasks": [ "..." ]
}
```

### 새 WebSocket 이벤트

| 이벤트 `type` | 트리거 API | 추가 필드 |
| --- | --- | --- |
| `epic:status_changed` | `PATCH /epics/{epicId}/status` | `epicId`, `status`, `closedAt` |
| `epic:reordered` | `PATCH /epics/reorder` | `orderedIds` |

기존 `epic:created`, `epic:updated`, `epic:deleted`는 확장된 `EpicResponse`를 반환하도록 업데이트.

### 에러 코드 추가

| `code` | HTTP | 설명 |
| --- | --- | --- |
| `EPIC_STATUS_NOT_FOUND` | 400 | 유효하지 않은 EpicStatus |

---

## 제안 9 — 이슈 번호 통합 시퀀스 vs 타입별 시퀀스 결정 요청

### 배경

현재 스토리는 팀스페이스 내 순차 `number`를 가진다 (`FE-001`, `BE-002` 등).  
제안 7·8을 통해 최상위 태스크와 에픽도 `number`가 필요해진다.

### 선택지

| 방식 | 예시 | 장점 | 단점 |
| --- | --- | --- | --- |
| **통합 시퀀스** | EP-001, FE-002, BE-003 (이슈 타입 무관 순서) | 번호로 전체 이슈 탐색 가능 | 이슈 유형 추가 시 시퀀스 복잡 |
| **타입별 시퀀스** | EP-001, ST-001, TK-001 (타입별 독립 번호) | 타입마다 1번부터 시작, 직관적 | 같은 번호가 여러 타입에 존재 |
| **현행 유지 (스토리만)** | FE-001 (스토리만 번호) | 변경 최소화 | 에픽/태스크 번호 없음 |

**확인 요청:** 에픽과 최상위 태스크에 `number`를 부여할 때 기존 스토리 시퀀스와 통합할지, 별도로 관리할지 결정 필요.

---

## 추가 발견 사항

_(구현 중 발견되는 추가 사항을 여기에 기록)_

| 날짜       | 내용                |
| ---------- | ------------------- |
| 2026-05-26 | 초기 제안 사항 작성 |
| 2026-05-26 | 제안 7·8·9 추가 — 이슈 유형 선택(스토리/태스크) 및 에픽 백로그 통합 |
