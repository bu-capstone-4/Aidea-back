# Gito Phase 2 — WebSocket + Yjs 릴레이

> **전제 조건:** [Phase 1](./gito-phase1-foundation.md) 완료 — `Document`, `DocumentUpdate` 엔티티, `DocumentService.saveUpdate()` / `getSnapshot()` / `getPendingUpdates()` 구현 완료
> **필독:** 이 문서를 읽기 전에 [gito-index.md](./gito-index.md)를 반드시 먼저 읽어라.
> 특히 **WebSocket 메시지 프로토콜** (index 문서 내)을 숙지한 후 이 문서를 읽을 것.

---

## 완료 조건

이 Phase가 끝나면 다음이 가능해야 한다:
- UserA가 `/ws/documents/{docId}`에 연결하면 `doc:init` 이벤트를 받아 문서 상태를 복원함
- UserB가 연결하면 마찬가지로 동일한 `doc:init`을 받음
- UserA가 `doc:update`를 보내면 인메모리 버퍼에 저장되고 DB에 INSERT되며 UserB에게 브로드캐스트됨
- VIEWER 역할 유저는 `doc:update` 메시지를 보내도 서버가 무시함
- 팀스페이스 비소속자는 WebSocket 연결 자체가 거부됨

---

## 구현 순서

1. `DocumentUpdateBuffer` — 인메모리 버퍼 (의존성 없음, 먼저 구현)
2. `WebSocketConfig` — 엔드포인트 등록
3. `DocumentHandshakeInterceptor` — 연결 시 권한 검사
4. `DocumentWebSocketHandler` — 이벤트 처리 핵심

---

## 1. DocumentUpdateBuffer — 인메모리 버퍼

WebSocket 핸들러와 스케줄러(Phase 3)가 함께 사용하는 공유 컴포넌트다.

```java
@Component
public class DocumentUpdateBuffer {

    // docId → 수신 순서가 보장된 업데이트 바이너리 목록
    private final ConcurrentHashMap<String, List<byte[]>> buffer = new ConcurrentHashMap<>();

    // WS 핸들러가 업데이트 수신 시 호출
    public void push(String docId, byte[] updateBinary) {
        buffer.computeIfAbsent(docId, k -> Collections.synchronizedList(new ArrayList<>()))
              .add(updateBinary);
    }

    // 스케줄러 전용: 현재 버퍼를 반환하고 해당 docId 버퍼를 새 빈 리스트로 교체
    // ConcurrentHashMap.put은 원자적이므로, 교체 직후 들어오는 push는 새 리스트에 쌓임 → 유실 없음
    public List<byte[]> drainAndReset(String docId) {
        List<byte[]> drained = buffer.put(docId, Collections.synchronizedList(new ArrayList<>()));
        return drained != null ? new ArrayList<>(drained) : Collections.emptyList();
    }

    public boolean hasUpdates(String docId) {
        List<byte[]> list = buffer.get(docId);
        return list != null && !list.isEmpty();
    }

    // 스케줄러가 활성 docId 목록을 순회할 때 사용
    public Set<String> getActiveDocIds() {
        return buffer.keySet();
    }
}
```

**동시성 설계 포인트:**
- `computeIfAbsent`는 ConcurrentHashMap 자체가 원자적으로 처리
- 리스트 자체는 `Collections.synchronizedList`로 동시 `add` 안전하게 처리
- `drainAndReset` — `put`이 원자적이므로 drain 중 새 push가 유실되지 않음

---

## 2. WebSocketConfig

```java
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final DocumentWebSocketHandler documentWebSocketHandler;
    private final DocumentHandshakeInterceptor documentHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(documentWebSocketHandler, "/ws/documents/{docId}")
                .setAllowedOrigins("*")
                .addInterceptors(documentHandshakeInterceptor);
    }
}
```

---

## 3. DocumentHandshakeInterceptor — 연결 시 권한 검사

WebSocket 연결 수립 시점(HTTP Upgrade 요청)에 권한을 검사한다. 여기서 거부되면 연결 자체가 성립하지 않는다.

```java
@Component
@RequiredArgsConstructor
public class DocumentHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final DocumentRepository documentRepository;
    private final TeamspaceMemberRepository teamspaceMemberRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {

        // 1. URL에서 docId 추출
        String path = request.getURI().getPath();
        String docId = path.substring(path.lastIndexOf('/') + 1);

        // 2. 쿼리 파라미터 또는 헤더에서 JWT 추출
        String token = extractToken(request);
        if (token == null || !jwtTokenProvider.validate(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String userId = jwtTokenProvider.getUserId(token);

        // 3. 문서 존재 확인
        Document doc = documentRepository.findById(docId).orElse(null);
        if (doc == null) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }

        // 4. 팀스페이스 소속 여부 확인 (VIEWER 포함 소속이면 연결 허용)
        Optional<TeamspaceMember> member = teamspaceMemberRepository
            .findByTeamspaceIdAndUserId(doc.getTeamspace().getId(), userId);

        if (member.isEmpty()) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false; // 비소속자 연결 거부
        }

        // 5. 핸들러에서 사용할 정보 저장
        attributes.put("docId", docId);
        attributes.put("userId", userId);
        attributes.put("role", member.get().getRole());

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}

    private String extractToken(ServerHttpRequest request) {
        // Authorization 헤더 먼저 확인
        List<String> authHeaders = request.getHeaders().get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String bearer = authHeaders.get(0);
            if (bearer.startsWith("Bearer ")) return bearer.substring(7);
        }
        // 쿼리 파라미터 fallback: ?token=xxx (WebSocket은 헤더 설정이 어려운 경우 있음)
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) return param.substring(6);
            }
        }
        return null;
    }
}
```

---

## 4. DocumentWebSocketHandler — 핵심

```java
@Component
@RequiredArgsConstructor
public class DocumentWebSocketHandler extends TextWebSocketHandler {

    // 문서별 연결된 세션 목록
    // ConcurrentHashMap.newKeySet()으로 생성해야 thread-safe 보장 — HashSet 절대 금지
    private final ConcurrentHashMap<String, Set<WebSocketSession>> docSessions
            = new ConcurrentHashMap<>();

    private final DocumentUpdateBuffer updateBuffer;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    // --- 연결 수립 ---

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String docId = (String) session.getAttributes().get("docId");
        docSessions.computeIfAbsent(docId, k -> ConcurrentHashMap.newKeySet()).add(session);

        // 신규 접속자에게 현재 문서 상태 전송 (doc:init 이벤트)
        sendDocInit(session, docId);
    }

    // --- 메시지 수신 ---

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
        String type = (String) payload.get("type");

        switch (type) {
            case "doc:update" -> handleDocUpdate(session, payload);
            // 추후 이벤트 타입 추가 시 여기에 case 추가
        }
    }

    // --- 연결 종료 ---

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String docId = (String) session.getAttributes().get("docId");
        Set<WebSocketSession> sessions = docSessions.get(docId);
        if (sessions != null) {
            sessions.remove(session);
            // 빈 Set은 남겨두고 스케줄러가 머지 후 자연스럽게 정리되도록 함
        }
    }

    // --- 내부 처리 메서드 ---

    private void sendDocInit(WebSocketSession session, String docId) throws Exception {
        // DB에서 yjs_snapshot + 미머지 document_updates 조회
        byte[] snapshot = documentService.getSnapshot(docId);              // nullable
        List<byte[]> dbUpdates = documentService.getPendingUpdates(docId); // id ASC 순서 보장

        List<String> updates = new ArrayList<>();
        if (snapshot != null) {
            updates.add(Base64.getEncoder().encodeToString(snapshot));
        }
        for (byte[] u : dbUpdates) {
            updates.add(Base64.getEncoder().encodeToString(u));
        }

        // doc:init 이벤트 전송 (index 문서의 프로토콜 참고)
        Map<String, Object> event = Map.of("type", "doc:init", "updates", updates);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
    }

    private void handleDocUpdate(WebSocketSession session, Map<String, Object> payload) throws Exception {
        MemberRole role = (MemberRole) session.getAttributes().get("role");

        // VIEWER는 업데이트 불가 — 연결은 유지하되 수신 메시지 무시
        if (role == MemberRole.VIEWER) return;

        String docId = (String) session.getAttributes().get("docId");
        String clientId = (String) session.getAttributes().get("userId");
        String base64Update = (String) payload.get("update");
        byte[] updateBinary = Base64.getDecoder().decode(base64Update);

        // 1. 인메모리 버퍼에 push (Phase 3 스케줄러가 배치 머지 시 사용)
        updateBuffer.push(docId, updateBinary);

        // 2. DB에 영속 저장 (서버 재시작 시 버퍼 복구용 영속 로그)
        //    @Transactional은 documentService 내부에 있으므로 여기서는 위임
        documentService.saveUpdate(docId, updateBinary, clientId);

        // 3. 같은 문서의 다른 세션에 브로드캐스트 (발신자 제외)
        //    base64Update를 재사용 — 디코딩 후 재인코딩 불필요
        String broadcastJson = objectMapper.writeValueAsString(
            Map.of("type", "doc:update", "update", base64Update)
        );
        broadcastToOthers(session, docId, new TextMessage(broadcastJson));
    }

    private void broadcastToOthers(WebSocketSession sender, String docId, TextMessage message) {
        Set<WebSocketSession> sessions = docSessions.getOrDefault(docId, Collections.emptySet());
        for (WebSocketSession s : sessions) {
            if (s.isOpen() && !s.getId().equals(sender.getId())) {
                try {
                    s.sendMessage(message);
                } catch (IOException e) {
                    // 끊긴 세션은 afterConnectionClosed에서 정리됨 — 여기서는 무시
                }
            }
        }
    }

    // Phase 4의 GeminiService가 피드백 이벤트 푸시 시 사용하는 public API
    public void pushToDocument(String docId, String jsonEvent) {
        TextMessage message = new TextMessage(jsonEvent);
        Set<WebSocketSession> sessions = docSessions.getOrDefault(docId, Collections.emptySet());
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(message);
                } catch (IOException ignored) {}
            }
        }
    }
}
```

---

## 업데이트 이중 저장 흐름 (시퀀스 요약)

```
클라이언트 → { type: "doc:update", update: "<base64>" }
    │
    ├─ 1. updateBuffer.push(docId, binary)     ← 인메모리 버퍼 (스케줄러 캐시)
    ├─ 2. documentService.saveUpdate(...)       ← DB INSERT (재시작 복구용)
    └─ 3. broadcastToOthers(...)               ← 다른 클라이언트에 실시간 전달
```

---

## Phase 2 완료 체크리스트

- [ ] 팀스페이스 비소속자가 `/ws/documents/{docId}` 연결 시도 시 거부됨 (HTTP 403)
- [ ] VIEWER가 연결 후 `doc:update`를 보내도 DB에 저장되지 않음
- [ ] UserA 연결 → `doc:init` 수신 → UserB 연결 → `doc:init` 수신 (동일 상태)
- [ ] UserA가 `doc:update` 전송 → UserB가 `doc:update` 수신 (UserA는 받지 않음)
- [ ] `document_updates` 테이블에 UserA의 업데이트가 INSERT됨
- [ ] `DocumentUpdateBuffer`에 해당 docId의 update가 push됨 (디버그 로그로 확인)
