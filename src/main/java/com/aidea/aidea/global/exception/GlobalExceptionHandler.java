package com.aidea.aidea.global.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.aidea.aidea.global.dto.TestGlobalResponseDTO;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. 모든 RuntimeException 처리
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<TestGlobalResponseDTO<?>> handleRuntimeException(RuntimeException e) {

        return ResponseEntity
                .badRequest()
                .body(TestGlobalResponseDTO.error("RUNTIME_ERROR", e.getMessage()));
    }

    // 2. IllegalArgumentException 처리 
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<TestGlobalResponseDTO<?>> handleIllegalArgumentException(IllegalArgumentException e) {

        return ResponseEntity
                .badRequest()
                .body(TestGlobalResponseDTO.error("INVALID_ARGUMENT", e.getMessage()));
    }

    // 3. 모든 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<TestGlobalResponseDTO<?>> handleException(Exception e) {

        return ResponseEntity
                .status(500)
                .body(TestGlobalResponseDTO.error("INTERNAL_SERVER_ERROR", "서버 오류 발생"));
    }
}
