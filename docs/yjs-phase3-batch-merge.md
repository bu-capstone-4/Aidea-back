# Gito Phase 3 — 배치 머지 스케줄러

> **전제 조건:** [Phase 2](./gito-phase2-websocket.md) 완료 — `DocumentUpdateBuffer`, `DocumentWebSocketHandler`, `DocumentUpdateRepository` 구현 완료
> **필독:** 이 문서를 읽기 전에 [gito-index.md](./gito-index.md)를 반드시 먼저 읽어라.

---

## 완료 조건

이 Phase가 끝나면 다음이 가능해야 한다:
- 5분 주기로 스케줄러가 실행되어 인메모리 버퍼의 업데이트를 `documents.yjs_snapshot`에 머지함
- 머지 완료 후 대응하는 `document_updates` rows가 DELETE됨
- 두 작업(UPDATE + DELETE)이 같은 트랜잭션 내에서 원자적으로 처리됨
- 서버 재시작 시 버퍼가 초기화되어도 DB의 미머지 rows를 폴백으로 처리함

---

## 배치 머지 흐름 (시퀀스 다이어그램 기반)

```
Scheduler 실행
    │
    ├─ 1. updateBuffer.drainAndReset(docId)          ← 인메모리 버퍼에서 드레인
    │       └─ 빈 새 리스트로 교체 (이후 push는 새 리스트에 쌓임)
    │
    ├─ 2. [같은 @Transactional 내]
    │   ├─ UPDATE documents SET yjs_snapshot = mergedBinary ...  ← 스냅샷 갱신
    │   └─ DELETE FROM document_updates WHERE id IN (...)        ← 대응 rows 삭제
    │
    └─ 3. (재시작 폴백) DB에 미머지 rows 있으면 동일하게 처리
```

**핵심 불변식:** UPDATE와 DELETE는 반드시 같은 트랜잭션이어야 한다.
- UPDATE 성공 + DELETE 실패 → 다음 `doc:init`에서 이미 머지된 업데이트가 다시 전송됨 → 문서 깨짐
- UPDATE 실패 + DELETE 성공 → 업데이트 유실

---

## 1. AsyncConfig — 스케줄러 활성화

```java
@Configuration
@EnableScheduling
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

---

## 2. DocumentUpdateRepository — 필요한 쿼리 메서드

Phase 1에서 선언한 것 외에 아래 메서드가 추가로 필요하다.

```java
public interface DocumentUpdateRepository extends JpaRepository<DocumentUpdate, Long> {

    // Phase 2에서 선언 — 신규 접속자에게 미머지 업데이트 전송
    List<DocumentUpdate> findByDocumentIdOrderByIdAsc(String documentId);

    // 배치 머지 시: 가장 오래된 N개의 id 조회 (버퍼 드레인 크기와 일치시킴)
    // MySQL native query는 LIMIT :n 파라미터 바인딩 미지원 → Pageable로 처리
    @Query("SELECT u.id FROM DocumentUpdate u WHERE u.document.id = :docId ORDER BY u.id ASC")
    List<Long> findTopNIdsByDocumentIdOrderByIdAsc(
        @Param("docId") String docId,
        Pageable pageable  // 호출 시: PageRequest.of(0, n)
    );

    // 재시작 폴백: 미머지 rows가 존재하는 docId 목록
    @Query("SELECT DISTINCT u.document.id FROM DocumentUpdate u")
    List<String> findDocIdsWithPendingUpdates();

    // 머지 완료된 rows 일괄 삭제 (id 목록 기반)
    // deleteAllByIdInBatch는 JpaRepository 기본 제공 — 추가 구현 불필요
}
```

---

## 3. YjsMergeScheduler

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class YjsMergeScheduler {

    private final DocumentUpdateBuffer updateBuffer;
    private final DocumentRepository documentRepository;
    private final DocumentUpdateRepository documentUpdateRepository;

    // fixedDelay: 이전 실행이 완료된 후 5분 대기 → 동시 실행 자동 방지
    // fixedRate 사용 금지: 이전 실행이 느리면 중복 실행 발생
    @Scheduled(fixedDelay = 300_000)
    public void mergeUpdates() {
        // --- 1단계: 인메모리 버퍼 기반 머지 ---
        for (String docId : updateBuffer.getActiveDocIds()) {
            if (!updateBuffer.hasUpdates(docId)) continue;

            List<byte[]> bufferedUpdates = updateBuffer.drainAndReset(docId);
            if (bufferedUpdates.isEmpty()) continue;

            try {
                mergeAndPersist(docId, bufferedUpdates);
                log.debug("Batch merge completed: docId={}, count={}", docId, bufferedUpdates.size());
            } catch (Exception e) {
                // 실패 시 드레인된 업데이트가 버퍼에서는 이미 제거됨
                // DB에는 여전히 rows가 있으므로 다음 폴백 단계에서 처리됨
                log.error("Batch merge failed for docId={}", docId, e);
            }
        }

        // --- 2단계: 재시작 폴백 — 버퍼에 없지만 DB에 미머지 rows가 있는 경우 처리 ---
        // 서버 재시작 직후 버퍼가 비어있을 때만 실제로 동작함
        // 정상 운영 중에는 1단계에서 이미 처리된 docId는 여기서 스킵됨
        try {
            List<String> dbOnlyDocIds = documentUpdateRepository.findDocIdsWithPendingUpdates();
            for (String docId : dbOnlyDocIds) {
                if (updateBuffer.hasUpdates(docId)) continue; // 1단계에서 처리 중이면 스킵

                List<DocumentUpdate> dbUpdates = documentUpdateRepository
                    .findByDocumentIdOrderByIdAsc(docId);
                if (dbUpdates.isEmpty()) continue;

                List<byte[]> binaries = dbUpdates.stream()
                    .map(DocumentUpdate::getUpdateBinary)
                    .toList();

                mergeAndPersist(docId, binaries);
                log.info("Fallback merge completed after restart: docId={}, count={}", docId, binaries.size());
            }
        } catch (Exception e) {
            log.error("Fallback merge failed", e);
        }
    }

    // UPDATE + DELETE를 같은 트랜잭션으로 처리 — 원자성 보장
    // @Transactional 메서드를 별도로 분리하는 이유:
    // @Scheduled 메서드에 직접 @Transactional을 붙이면 Spring AOP 프록시가 적용되지 않을 수 있음
    @Transactional
    public void mergeAndPersist(String docId, List<byte[]> updates) {
        Document document = documentRepository.findById(docId)
            .orElseThrow(() -> new RuntimeException("Document not found: " + docId));

        // 기존 스냅샷 + 버퍼 업데이트들을 순서대로 이어붙임
        // 서버는 Yjs를 파싱하지 않으므로 단순 byte concat
        // 클라이언트가 순서대로 Y.applyUpdate()로 풀어냄
        byte[] newSnapshot = concatWithSnapshot(document.getYjsSnapshot(), updates);

        document.setYjsSnapshot(newSnapshot);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);

        // 버퍼에서 드레인된 것과 같은 수(updates.size())만큼 DB에서 가장 오래된 것부터 삭제
        List<Long> toDeleteIds = documentUpdateRepository
            .findTopNIdsByDocumentIdOrderByIdAsc(docId, PageRequest.of(0, updates.size()));
        documentUpdateRepository.deleteAllByIdInBatch(toDeleteIds);
    }

    // 기존 스냅샷 뒤에 업데이트들을 순서대로 이어붙임
    private byte[] concatWithSnapshot(byte[] snapshot, List<byte[]> updates) {
        int snapshotLen = snapshot != null ? snapshot.length : 0;
        int updatesLen = updates.stream().mapToInt(b -> b.length).sum();
        byte[] result = new byte[snapshotLen + updatesLen];

        int offset = 0;
        if (snapshot != null) {
            System.arraycopy(snapshot, 0, result, 0, snapshot.length);
            offset += snapshot.length;
        }
        for (byte[] update : updates) {
            System.arraycopy(update, 0, result, offset, update.length);
            offset += update.length;
        }
        return result;
    }
}
```

---

## 4. 주의사항

### fixedDelay vs fixedRate

```java
// 올바름: 이전 실행 완료 후 5분 대기
@Scheduled(fixedDelay = 300_000)

// 금지: 이전 실행이 5분을 넘어도 새 실행이 시작됨 → 동시 머지 발생 위험
@Scheduled(fixedRate = 300_000)
```

### drainAndReset 이후 실패 시 처리

`drainAndReset`으로 버퍼에서 꺼낸 후 `mergeAndPersist`가 예외를 던지면:
- 버퍼에서는 이미 제거됨 (복구 불가)
- DB의 `document_updates`에는 rows가 여전히 존재

따라서 다음 스케줄러 실행 시 폴백 단계(2단계)에서 DB rows를 다시 머지한다.
이 동작이 올바르게 작동하려면 DB INSERT(Phase 2에서 수행)가 버퍼 push보다 먼저 실패하지 않아야 한다.

### mergeAndPersist를 별도 메서드로 분리하는 이유

`@Scheduled` 메서드에 직접 `@Transactional`을 붙이면 Spring AOP 프록시가 정상 적용되지 않을 수 있다. `mergeAndPersist`를 같은 Bean 내의 별도 `public @Transactional` 메서드로 분리하더라도 **self-invocation 문제**가 발생한다.

가장 안전한 방법: `mergeAndPersist`를 별도 Service 클래스(`YjsMergeService`)로 추출하고 `YjsMergeScheduler`에서 주입받아 호출한다.

```java
// 권장 구조
@Service
public class YjsMergeService {
    @Transactional
    public void mergeAndPersist(String docId, List<byte[]> updates) { ... }
}

@Component
@RequiredArgsConstructor
public class YjsMergeScheduler {
    private final YjsMergeService yjsMergeService;

    @Scheduled(fixedDelay = 300_000)
    public void mergeUpdates() {
        // ...
        yjsMergeService.mergeAndPersist(docId, bufferedUpdates);
    }
}
```

---

## Phase 3 완료 체크리스트

- [ ] 스케줄러가 5분 주기로 실행됨 (로그 확인)
- [ ] 머지 후 `documents.yjs_snapshot`이 갱신됨 (DB 직접 확인)
- [ ] 머지 후 `document_updates` rows가 삭제됨 (DB 직접 확인)
- [ ] UPDATE + DELETE 중 하나가 실패하면 롤백되어 둘 다 원상태가 됨 (트랜잭션 테스트)
- [ ] 서버 재시작 후 DB에 미머지 rows가 있으면 다음 스케줄러 실행 시 폴백으로 처리됨
- [ ] 신규 접속자가 머지 후 연결하면 더 작아진 `doc:init` (단일 스냅샷만) 수신
