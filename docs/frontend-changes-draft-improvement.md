# 프론트엔드 대응 요청: 초안 생성 시스템 개선

> 백엔드 브랜치: `hotfix/feedback-prompt`  
> 관련 백엔드 이슈: 6-1 (idea 필드 미전달), 6-2 (TeamSpaceStatus 영구 CREATING), 6-3 (draft 이벤트 채널 오류)

---

## 변경 범위 한눈에 보기

| 구분 | 변경 내용 | 영향 |
|------|-----------|------|
| REST API | 팀스페이스 응답에서 `status` 필드 제거 | **Breaking** |
| REST API | 팀스페이스 상세 응답의 문서 항목에 `aiStatus` 필드 추가 | 신규 활용 필요 |
| REST API | `PUT /api/teamspaces/{id}` 요청 body에서 `status` 필드 무시 | Breaking |
| WebSocket | `teamspace:init` 이벤트의 `teamspace.status` 제거 | **Breaking** |
| WebSocket | `draft:ready`, `draft:error` 이벤트 채널이 **문서 → 팀스페이스**로 변경 | **Breaking** |

---

## 1. REST API 변경 명세

### 1-1. `POST /api/teamspaces` — 팀스페이스 생성

#### 응답 변경

`status` 필드 제거.

**변경 전**
| 필드 | 타입 | 설명 |
|------|------|------|
| teamspaceId | String | 팀스페이스 ID |
| name | String | 이름 |
| status | String | `"CREATING"` 고정 (더 이상 존재하지 않음) |
| createdAt | DateTime | 생성일시 |

**변경 후**
| 필드 | 타입 | 설명 |
|------|------|------|
| teamspaceId | String | 팀스페이스 ID |
| name | String | 이름 |
| createdAt | DateTime | 생성일시 |

> **프론트 대응**: 생성 직후 `status`를 읽어 "초기화 중" 여부를 판단하던 로직은 제거. 초기화 상태 표시는 [1-3 상세 조회](#1-3-get-apiteamspacesid--팀스페이스-상세-조회)의 `aiStatus`로 대체.

---

### 1-2. `GET /api/teamspaces` — 팀스페이스 목록 조회

#### 응답 변경

목록 항목에서 `status` 필드 제거.

**변경 전 — `teamspaces[]` 항목**
| 필드 | 타입 | 설명 |
|------|------|------|
| teamspaceId | String | |
| name | String | |
| status | String | `"CREATING"` 고정 (제거됨) |
| createdAt | DateTime | |

**변경 후 — `teamspaces[]` 항목**
| 필드 | 타입 | 설명 |
|------|------|------|
| teamspaceId | String | |
| name | String | |
| createdAt | DateTime | |

> **프론트 대응**: 목록에서 `status`를 표시하거나 읽던 곳 제거.

---

### 1-3. `GET /api/teamspaces/{id}` — 팀스페이스 상세 조회

#### 응답 변경 — 팀스페이스 레벨

`status` 필드 제거.

**변경 전**
| 필드 | 타입 | 설명 |
|------|------|------|
| teamspaceId | String | |
| name | String | |
| status | String | `"CREATING"` 고정 (제거됨) |
| createdAt | DateTime | |
| documents | Array | |

**변경 후**
| 필드 | 타입 | 설명 |
|------|------|------|
| teamspaceId | String | |
| name | String | |
| createdAt | DateTime | |
| documents | Array | |

#### 응답 변경 — `documents[]` 항목

`aiStatus` 필드 추가.

**변경 전**
| 필드 | 타입 | 설명 |
|------|------|------|
| id | String | 문서 ID |
| type | String | 문서 타입 |
| title | String | 제목 |
| updatedAt | DateTime | 수정일 |
| updatedBy | String | 수정자 userId |

**변경 후**
| 필드 | 타입 | 설명 |
|------|------|------|
| id | String | 문서 ID |
| type | String | 문서 타입 |
| title | String | 제목 |
| **aiStatus** | String | AI 처리 상태 (아래 값 목록 참고) |
| updatedAt | DateTime | 수정일 |
| updatedBy | String | 수정자 userId |

**`aiStatus` 가능한 값**

| 값 | 의미 |
|----|------|
| `IDLE` | 일반 상태 (AI 작업 없음) |
| `DRAFT` | AI 초안 생성 진행 중 |
| `FEEDBACK_IN_PROGRESS` | AI 피드백 진행 중 |

> **프론트 대응 — 초기화 로딩 상태 표시 변경**
>
> 기존에 팀스페이스 `status === "CREATING"`으로 "초기화 중" UI를 표시하던 로직을 아래 기준으로 교체한다:
>
> - `documents` 배열 내 `aiStatus === "DRAFT"` 인 항목이 **하나라도 존재**하면 → 해당 팀스페이스는 초안 생성이 진행 중
> - 모든 문서의 `aiStatus`가 `"IDLE"`이면 → 초기화 완료
>
> 이 값은 REST 폴링 또는 WebSocket `draft:ready` / `draft:error` 수신 후 상세 조회 재호출로 갱신한다.

---

### 1-4. `PUT /api/teamspaces/{id}` — 팀스페이스 수정

#### 요청 변경

`status` 필드가 요청 body에서 제거됨. 포함해도 서버에서 무시한다.

**변경 전**
| 필드 | 타입 | 설명 |
|------|------|------|
| name | String | (선택) 수정할 이름 |
| status | String | (선택) 수정할 상태 → **제거** |

**변경 후**
| 필드 | 타입 | 설명 |
|------|------|------|
| name | String | (선택) 수정할 이름 |

#### 응답 변경

생성 응답과 동일하게 `status` 제거.

---

## 2. WebSocket 변경 명세

### 2-1. 팀스페이스 WebSocket — `teamspace:init` 이벤트

팀스페이스 WebSocket 연결 직후 서버에서 전송하는 초기화 이벤트.

#### `data.teamspace` 객체 변경

**변경 전**
| 필드 | 타입 | 설명 |
|------|------|------|
| id | String | 팀스페이스 ID |
| name | String | 이름 |
| status | String | `"CREATING"` 고정 (제거됨) |

**변경 후**
| 필드 | 타입 | 설명 |
|------|------|------|
| id | String | 팀스페이스 ID |
| name | String | 이름 |

> **프론트 대응**: `teamspace:init` 핸들러에서 `data.teamspace.status`를 읽는 코드 제거.

---

### 2-2. 이벤트 채널 변경 — `draft:ready`, `draft:error`

> **이것이 가장 큰 Breaking Change다.**

기존에 `draft:ready`, `draft:error` 이벤트는 **문서 WebSocket** (`/ws/documents/{docId}`) 채널로 수신됐다. 이번 변경으로 **팀스페이스 WebSocket** (`/ws/teamspaces/{teamspaceId}`) 채널로 이동한다.

| | 변경 전 | 변경 후 |
|--|---------|---------|
| 수신 채널 | 문서 WebSocket | **팀스페이스 WebSocket** |
| 수신 타이밍 | 해당 문서 편집 화면 진입 후 | **팀스페이스 화면 진입 후** (문서 진입 전에도 수신 가능) |

#### `draft:ready` 이벤트 명세

팀스페이스 WebSocket에서 수신. 초안 생성이 완료됐을 때 서버가 전송.

**이벤트 구조**

| 필드 | 타입 | 설명 |
|------|------|------|
| event | String | `"draft:ready"` |
| data.documentId | String | 초안이 생성된 문서 ID |
| data.draftId | String | 생성된 초안 ID |
| data.content | String | 생성된 마크다운 초안 본문 |

> **프론트 대응**:
> - 팀스페이스 WebSocket 핸들러에 `draft:ready` 케이스 추가
> - 수신 후 해당 `documentId`의 `aiStatus`를 `"IDLE"`로 업데이트 (또는 상세 조회 재호출)
> - 사용자가 해당 문서를 열어보기 전이라도 이벤트를 받아 상태 갱신 가능

#### `draft:error` 이벤트 명세

팀스페이스 WebSocket에서 수신. 초안 생성이 실패했을 때 서버가 전송.

**이벤트 구조**

| 필드 | 타입 | 설명 |
|------|------|------|
| event | String | `"draft:error"` |
| data.documentId | String | 초안 생성에 실패한 문서 ID |

> **프론트 대응**:
> - 팀스페이스 WebSocket 핸들러에 `draft:error` 케이스 추가
> - 수신 후 해당 `documentId`의 `aiStatus`를 `"IDLE"`로 업데이트하고 에러 UI 표시

#### 문서 WebSocket에서 제거

문서 WebSocket (`/ws/documents/{docId}`)에서 더 이상 `draft:ready`, `draft:error`가 전송되지 않는다. 해당 채널에 이 이벤트에 대한 핸들러가 있다면 제거한다.

---

## 3. 프론트엔드 작업 체크리스트

### Breaking 대응 (필수)

- [ ] `POST /api/teamspaces` 응답에서 `.status` 참조 제거
- [ ] `GET /api/teamspaces` 목록 응답에서 각 항목의 `.status` 참조 제거
- [ ] `GET /api/teamspaces/{id}` 응답에서 팀스페이스 레벨 `.status` 참조 제거
- [ ] `PUT /api/teamspaces/{id}` 요청 body에서 `status` 필드 제거
- [ ] `teamspace:init` WebSocket 이벤트 핸들러에서 `data.teamspace.status` 참조 제거
- [ ] 문서 WebSocket 핸들러에서 `draft:ready`, `draft:error` 케이스 제거
- [ ] 팀스페이스 WebSocket 핸들러에 `draft:ready`, `draft:error` 케이스 추가

### 신규 기능 활용 (필수)

- [ ] 팀스페이스 상세 응답의 `documents[].aiStatus` 값을 이용해 문서별 초안 생성 진행 상태 표시
- [ ] 팀스페이스 생성 후 "초기화 중" 상태 판단 기준을 `status === "CREATING"` → `documents.some(d => d.aiStatus === "DRAFT")`로 변경
- [ ] `draft:ready` 수신 시 해당 문서의 `aiStatus` 갱신 및 초안 내용 활용 처리
- [ ] `draft:error` 수신 시 에러 알림 UI 처리

---

## 4. 배포 일정 협의 필요 사항

이번 변경은 REST API 응답 구조와 WebSocket 이벤트 채널이 동시에 변경되는 Breaking Change다. 백엔드 배포와 프론트엔드 배포가 분리되면 다음 문제가 발생한다.

- 신 백엔드 + 구 프론트: `status` 필드가 없어 "초기화 중" 표시가 깨짐 / `draft:ready` 수신 불가
- 구 백엔드 + 신 프론트: `aiStatus` 필드가 없어 문서 상태 표시 불가

**동시 배포**를 권장한다.
