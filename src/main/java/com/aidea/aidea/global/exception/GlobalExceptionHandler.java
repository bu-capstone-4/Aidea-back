package com.aidea.aidea.global.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.aidea.aidea.global.dto.TestGlobalResponseDTO;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<TestGlobalResponseDTO<?>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(TestGlobalResponseDTO.error(errorCode.getCode(), errorCode.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<TestGlobalResponseDTO<?>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity
                .badRequest()
                .body(TestGlobalResponseDTO.error("INVALID_ARGUMENT", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<TestGlobalResponseDTO<?>> handleException(Exception e) {
        return ResponseEntity
                .internalServerError()
                .body(TestGlobalResponseDTO.error("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."));
    }
}
