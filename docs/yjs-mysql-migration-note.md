# Yjs 문서 MySQL 마이그레이션 분석 노트

> PostgreSQL 기반으로 작성된 yjs-*.md 문서들을 MySQL로 전환하기 위한 변경 항목 정리.

---

## 변경 대상 파일

| 파일 | 변경 필요 여부 | 주요 변경 내용 |
|---|---|---|
| yjs-index.md | O | 아키텍처 다이어그램, 기술스택, JPA 컨벤션, BYTEA 주의사항 섹션 |
| yjs-phase1-foundation.md | O | 드라이버 의존성, application.yml, 엔티티 columnDefinition, 체크리스트 |
| yjs-phase2-websocket.md | X | PostgreSQL 전용 내용 없음 |
| yjs-phase3-batch-merge.md | X | PostgreSQL 전용 내용 없음 |
| yjs-phase4-ai-feedback.md | O | Feedback 엔티티 columnDefinition (JSONB × 2, BYTEA × 1), 체크리스트 |
| yjs-phase5-polish.md | O | 체크리스트 내 `\d documents` 명령어 |

---

## 핵심 타입 매핑

| PostgreSQL | MySQL | 용도 |
|---|---|---|
| `BYTEA` | `LONGBLOB` | Yjs 바이너리 (yjs_snapshot, update_binary, yjs_binary) |
| `JSONB` | `JSON` | 구조화 데이터 (questions, answers) — MySQL 5.7.8+부터 지원 |

- MySQL JSON 타입은 PostgreSQL JSONB와 달리 GIN 인덱스가 없음. 현재 설계에서는 JSON 컬럼을 인덱스로 조회하지 않으므로 영향 없음.
- 기존 `QuestionsConverter` / `AnswersConverter`는 `String` 직렬화 기반이므로 컬럼 타입만 변경하면 그대로 동작.

---

## 제거/변경 설정

- `hibernate.jdbc.lob.non_contextual_creation: true` — PostgreSQL BYTEA 처리 전용 설정, MySQL에서는 불필요하여 제거.
- Hibernate Dialect — Spring Boot 3.x + MySQL Connector J 사용 시 자동 감지되므로 별도 명시 불필요. 명시할 경우 `org.hibernate.dialect.MySQL8Dialect`.

---

## 기타 참고사항

- `BIGSERIAL` 주석 (DocumentUpdate.id) — MySQL의 `AUTO_INCREMENT`로 주석 수정 필요. 코드 자체(`GenerationType.IDENTITY`)는 양쪽 모두 동일하게 동작.
- `\d documents` (PostgreSQL psql 명령) → `DESCRIBE documents` (MySQL)로 체크리스트 수정.
- Phase 2, 3 헤더에 남아 있는 "Gito" 표기는 PostgreSQL 마이그레이션과 무관하여 이번 작업 범위 외.
