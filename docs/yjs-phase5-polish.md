# Gito Phase 5 — 완성도 (예외처리 · 유효성 검사 · 테스트)

> **전제 조건:** [Phase 1~4](./gito-index.md) 모두 완료
> **필독:** 이 문서를 읽기 전에 [gito-index.md](./gito-index.md)를 반드시 먼저 읽어라.

---

## 완료 조건

이 Phase가 끝나면 다음이 가능해야 한다:
- 모든 예외가 `GlobalExceptionHandler`에서 일관된 JSON 포맷으로 처리됨
- 유효하지 않은 요청 body에 대해 400 + 어떤 필드가 왜 유효하지 않은지 응답함
- 주요 흐름에 대한 통합 테스트가 통과함

---

## 1. CustomException 계층

```java
// 최상위 커스텀 예외 — HTTP 상태코드를 함께 가짐
public class CustomException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public CustomException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}

// 구체 예외들 — Service 레이어에서 throw
public class NotFoundException extends CustomException {
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}

public class ForbiddenException extends CustomException {
    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}

public class ConflictException extends CustomException {
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }
}

public class BadRequestException extends CustomException {
    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}
```

---

## 2. GlobalExceptionHandler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 커스텀 예외 처리
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        return ResponseEntity
            .status(e.getStatus())
            .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    // @Valid 유효성 검사 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException e) {

        String message = e.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining(", "));

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    // 인증 실패 (Spring Security)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("FORBIDDEN", "접근 권한이 없습니다"));
    }

    // 그 외 예상치 못한 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
    }
}

// 공통 에러 응답 DTO
public record ErrorResponse(String code, String message) {}
```

---

## 3. 유효성 검사 (@Valid 적용 위치)

### FeedbackRequest

```java
public record FeedbackRequest(
    @NotBlank(message = "문서 텍스트는 필수입니다")
    @Size(max = 10000, message = "문서 텍스트는 10000자를 초과할 수 없습니다")
    String documentText,

    @Size(max = 500, message = "추가 요청사항은 500자를 초과할 수 없습니다")
    String additionalRequest
) {}
```

### AnswerRequest

```java
public record AnswerRequest(
    @NotEmpty(message = "답변 목록은 비어있을 수 없습니다")
    @Valid
    List<AnswerDto> answers
) {}

public record AnswerDto(
    @NotBlank String questionId,
    @NotBlank String value
) {}
```

### CreateDocumentRequest

```java
public record CreateDocumentRequest(
    @NotBlank(message = "문서 제목은 필수입니다")
    @Size(max = 100, message = "제목은 100자를 초과할 수 없습니다")
    String title,

    @NotNull(message = "문서 타입은 필수입니다")
    DocumentType type
) {}
```

---

## 4. 통합 테스트 체크리스트

### Phase 1 — 기반

- [ ] `POST /api/auth/login` → JWT 토큰 발급
- [ ] 유효하지 않은 토큰으로 보호된 API 호출 시 401 반환
- [ ] 팀스페이스 비소속 유저로 문서 조회 시 403 반환
- [ ] VIEWER 유저로 문서 생성 시 403 반환
- [ ] `yjs_snapshot` 컬럼이 MySQL에서 `longblob` 타입인지 `DESCRIBE documents`로 확인

### Phase 2 — WebSocket

- [ ] 비소속 유저가 `/ws/documents/{docId}`에 연결 시 HTTP 403으로 거부됨
- [ ] VIEWER 유저가 연결 후 `doc:update` 전송 시 `document_updates` 테이블에 INSERT 없음
- [ ] 두 클라이언트 연결 후 한 쪽이 `doc:update` 전송 시 다른 쪽이 수신
- [ ] 신규 접속자가 `doc:init` 이벤트를 수신하고 `updates` 배열이 올바른 순서

### Phase 3 — 배치 머지

- [ ] 머지 전: `document_updates` rows 존재
- [ ] 스케줄러 실행 후: `documents.yjs_snapshot` 갱신, `document_updates` rows 삭제
- [ ] 머지 후 신규 접속자의 `doc:init`에 `updates` 배열이 빈 배열 (스냅샷만 있음)
- [ ] 서버 재시작 후 첫 스케줄러 실행 시 DB의 미머지 rows가 처리됨

### Phase 4 — AI 피드백

- [ ] `POST /api/documents/{docId}/feedback` → 즉시 202, `status: PENDING`
- [ ] 진행 중 재요청 시 409 반환
- [ ] `feedback:questioning` 이벤트 수신 후 `POST /api/feedbacks/{id}/answer` → `feedback:ready` 수신
- [ ] `POST /api/feedbacks/{id}/accept` → DB `status = ACCEPTED`
- [ ] `feedbacks.questions`, `feedbacks.answers` JSON이 올바르게 저장/역직렬화됨

### Phase 5 — 완성도

- [ ] 유효하지 않은 `FeedbackRequest` (빈 documentText) 시 400 + 필드 에러 메시지
- [ ] 존재하지 않는 feedbackId로 accept 시 404 + `NOT_FOUND` code
- [ ] `ANSWERING` 상태에서 accept 시 400 + `BAD_REQUEST` code
- [ ] 서버 내부 오류 발생 시 500 + `INTERNAL_ERROR` code (민감 정보 노출 없음)
