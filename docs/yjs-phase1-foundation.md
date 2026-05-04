# Yjs Phase 1 — 프로젝트 기반 설정

> **전제 조건:** 없음 (첫 번째 Phase)
> **필독:** 이 문서를 읽기 전에 [yjs-index.md](./yjs-index.md)를 반드시 먼저 읽어라.

---

## 완료 조건

이 Phase가 끝나면 다음이 가능해야 한다:
- Spring Boot 서버가 MySQL에 연결되어 정상 기동됨
- JWT 토큰으로 인증된 요청이 통과됨
- `documents`, `document_updates`, `teamspaces`, `teamspace_members`, `users` 테이블이 생성됨
- 팀스페이스 + 문서 기본 CRUD REST API가 동작함 (권한 검사 포함)

---

## 1. 프로젝트 초기 설정

### build.gradle.kts 주요 의존성

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // MySQL
    runtimeOnly("com.mysql:mysql-connector-j")
}
```

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:3306/${DB_NAME:root}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: update        # 개발 중 update, 운영 시 validate
    show-sql: false
    properties:
      hibernate:
        format_sql: false

jwt:
  secret: ${JWT_SECRET}       # 환경변수로 주입
  expiration-ms: 86400000     # 24시간
```

---

## 2. 엔티티 구현

### 2-1. User 엔티티

```java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    private String id; // UUID

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
```

### 2-2. Teamspace + TeamspaceMember 엔티티

```java
@Entity
@Table(name = "teamspaces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Teamspace {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
```

```java
@Entity
@Table(name = "teamspace_members",
    uniqueConstraints = @UniqueConstraint(columnNames = {"teamspace_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamspaceMember {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teamspace_id", nullable = false)
    private Teamspace teamspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;  // OWNER, MEMBER, VIEWER

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;
}
```

### 2-3. MemberRole ENUM

```java
public enum MemberRole {
    OWNER,   // 팀스페이스 관리 + 문서 읽기/쓰기
    MEMBER,  // 문서 읽기/쓰기
    VIEWER   // 문서 읽기만 가능
}
```

**권한 체크 레이어별 책임:**

| 레이어 | 검사 대상 | 구현 위치 |
|---|---|---|
| WebSocket 핸드셰이크 | 팀스페이스 소속 여부 (비소속이면 연결 거부) | `DocumentHandshakeInterceptor` |
| WebSocket 메시지 수신 | VIEWER 업데이트 차단 | `DocumentWebSocketHandler.handleDocUpdate()` |
| REST API | VIEWER 쓰기 차단, 비소속자 접근 차단 | `DocumentService` 내 역할 확인 |

### 2-4. Document 엔티티

```java
@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {

    @Id
    private String id; // UUID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teamspace_id", nullable = false)
    private Teamspace teamspace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType type; // IDEA, PLAN, USER_SCENARIO, API_SPEC, ERD

    @Column(nullable = false)
    private String title;

    // Yjs 최종 스냅샷 — 배치 머지 전까지 null
    // @Column(columnDefinition = "LONGBLOB") 반드시 명시: MySQL BLOB 타입 명시
    @Column(name = "yjs_snapshot", columnDefinition = "LONGBLOB")
    private byte[] yjsSnapshot;

    // 마지막 머지 시점의 Yjs 논리 시계값
    @Column(name = "snapshot_clock")
    private Integer snapshotClock;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    public static Document create(String id, Teamspace teamspace, DocumentType type, String title) {
        Document doc = new Document();
        doc.id = id;
        doc.teamspace = teamspace;
        doc.type = type;
        doc.title = title;
        return doc;
    }
}
```

### 2-5. DocumentType ENUM

```java
public enum DocumentType {
    IDEA,
    PLAN,
    USER_SCENARIO,
    API_SPEC,
    ERD
}
```

### 2-6. DocumentUpdate 엔티티

```java
@Entity
@Table(name = "document_updates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentUpdate {

    // AUTO_INCREMENT 자동증가 — 수신 순서 보장 핵심
    // 클라이언트 복원 시 ORDER BY id ASC로 조회하면 Yjs가 올바른 순서로 적용됨
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    // Y.encodeStateAsUpdate() 결과 바이너리
    @Column(name = "update_binary", columnDefinition = "LONGBLOB", nullable = false)
    private byte[] updateBinary;

    // 변경을 발생시킨 유저/탭 식별자
    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static DocumentUpdate create(Document document, byte[] updateBinary, String clientId) {
        DocumentUpdate u = new DocumentUpdate();
        u.document = document;
        u.updateBinary = updateBinary;
        u.clientId = clientId;
        u.createdAt = LocalDateTime.now();
        return u;
    }
}
```

---

## 3. JWT 인증 구현

### JwtTokenProvider

```java
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    public String createToken(String userId) {
        return Jwts.builder()
            .subject(userId)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(getKey())
            .compact();
    }

    public String getUserId(String token) {
        return Jwts.parser()
            .verifyWith(getKey())
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
    }

    public boolean validate(String token) {
        try {
            Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
```

### JwtAuthFilter

```java
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && jwtTokenProvider.validate(token)) {
            String userId = jwtTokenProvider.getUserId(token);
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
```

### SecurityConfig

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/ws/**").permitAll() // WebSocket은 HandshakeInterceptor에서 검사
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

---

## 4. Repository 인터페이스

```java
public interface DocumentRepository extends JpaRepository<Document, String> {

    List<Document> findByTeamspaceId(String teamspaceId);
}

public interface DocumentUpdateRepository extends JpaRepository<DocumentUpdate, Long> {

    // 신규 접속자에게 미머지 업데이트 전송용
    List<DocumentUpdate> findByDocumentIdOrderByIdAsc(String documentId);

    // 배치 머지 시 DB에서 가장 오래된 N개 id 조회 (Phase 3에서 사용)
    @Query("SELECT u.id FROM DocumentUpdate u WHERE u.document.id = :docId ORDER BY u.id ASC LIMIT :n")
    List<Long> findTopNIdsByDocumentIdOrderByIdAsc(
        @Param("docId") String docId,
        @Param("n") int n
    );

    // 재시작 폴백: 미머지 rows가 있는 docId 목록 (Phase 3에서 사용)
    @Query("SELECT DISTINCT u.document.id FROM DocumentUpdate u")
    List<String> findDocIdsWithPendingUpdates();
}

public interface TeamspaceMemberRepository extends JpaRepository<TeamspaceMember, String> {

    Optional<TeamspaceMember> findByTeamspaceIdAndUserId(String teamspaceId, String userId);
}
```

---

## 5. DocumentService — 권한 검사 패턴

Phase 2 이후의 Service들도 이 패턴을 동일하게 따른다.

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentUpdateRepository documentUpdateRepository;
    private final TeamspaceMemberRepository teamspaceMemberRepository;

    // 문서 메타데이터 조회 — VIEWER 이상 허용
    public DocumentDto getDocument(String docId, String requestUserId) {
        Document doc = documentRepository.findById(docId)
            .orElseThrow(() -> new NotFoundException("문서를 찾을 수 없습니다"));

        requireMembership(doc.getTeamspace().getId(), requestUserId); // VIEWER도 통과
        return DocumentDto.from(doc);
    }

    // 문서 생성 — MEMBER 이상 허용
    @Transactional
    public DocumentDto createDocument(String teamspaceId, CreateDocumentRequest req, String requestUserId) {
        requireRole(teamspaceId, requestUserId, MemberRole.MEMBER, MemberRole.OWNER);

        Document doc = Document.create(UUID.randomUUID().toString(), /* ... */);
        return DocumentDto.from(documentRepository.save(doc));
    }

    // WS 핸들러에서 위임받아 업데이트를 DB에 저장 (Phase 2에서 호출)
    @Transactional
    public void saveUpdate(String docId, byte[] updateBinary, String clientId) {
        Document doc = documentRepository.findById(docId).orElseThrow();
        documentUpdateRepository.save(DocumentUpdate.create(doc, updateBinary, clientId));
    }

    // 신규 접속자에게 전송할 스냅샷 조회 (Phase 2에서 호출)
    public byte[] getSnapshot(String docId) {
        return documentRepository.findById(docId)
            .map(Document::getYjsSnapshot)
            .orElse(null);
    }

    // 신규 접속자에게 전송할 미머지 업데이트 조회 (Phase 2에서 호출)
    public List<byte[]> getPendingUpdates(String docId) {
        return documentUpdateRepository.findByDocumentIdOrderByIdAsc(docId)
            .stream().map(DocumentUpdate::getUpdateBinary).toList();
    }

    // 팀스페이스 소속 여부만 확인 (VIEWER도 통과)
    private void requireMembership(String teamspaceId, String userId) {
        teamspaceMemberRepository.findByTeamspaceIdAndUserId(teamspaceId, userId)
            .orElseThrow(() -> new ForbiddenException("팀스페이스 소속이 아닙니다"));
    }

    // 특정 역할 이상 확인
    private void requireRole(String teamspaceId, String userId, MemberRole... allowedRoles) {
        TeamspaceMember member = teamspaceMemberRepository
            .findByTeamspaceIdAndUserId(teamspaceId, userId)
            .orElseThrow(() -> new ForbiddenException("팀스페이스 소속이 아닙니다"));

        if (Arrays.stream(allowedRoles).noneMatch(r -> r == member.getRole())) {
            throw new ForbiddenException("권한이 없습니다");
        }
    }
}
```

---

## Phase 1 완료 체크리스트

- [ ] MySQL 연결 확인 (`./gradlew bootRun` 정상 기동)
- [ ] 5개 테이블 DDL 자동 생성 확인 (`documents`, `document_updates`, `teamspaces`, `teamspace_members`, `users`)
- [ ] JWT 토큰 발급 + 검증 단위 테스트 통과
- [ ] `GET /api/documents/{docId}` 권한 없는 요청 시 403 반환 확인
- [ ] `@Column(columnDefinition = "LONGBLOB")` 컬럼이 MySQL에서 `longblob` 타입으로 생성됐는지 `DESCRIBE documents`로 확인
