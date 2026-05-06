package com.aidea.aidea.global.exception;

import com.aidea.aidea.global.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<?>> handleCustomException(CustomException e,
                                                                HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("[WARN] who={} action={} {} reason={} message={}",
                resolveUserId(), request.getMethod(), fullPath(request),
                errorCode.getCode(), errorCode.getMessage());
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgumentException(IllegalArgumentException e,
                                                                          HttpServletRequest request) {
        log.warn("[WARN] who={} action={} {} reason=INVALID_ARGUMENT message={}",
                resolveUserId(), request.getMethod(), fullPath(request), e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("INVALID_ARGUMENT", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e,
                                                          HttpServletRequest request) {
        log.error("[ERROR] who={} action={} {} unhandled exception",
                resolveUserId(), request.getMethod(), fullPath(request), e);
        return ResponseEntity
                .internalServerError()
                .body(ApiResponse.error("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."));
    }

    private String resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String id) {
            return id;
        }
        return "anonymous";
    }

    private String fullPath(HttpServletRequest request) {
        String query = request.getQueryString();
        return query != null ? request.getRequestURI() + "?" + query : request.getRequestURI();
    }
}
