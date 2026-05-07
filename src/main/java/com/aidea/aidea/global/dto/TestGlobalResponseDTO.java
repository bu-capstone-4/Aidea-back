package com.aidea.aidea.global.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestGlobalResponseDTO<T> {

    private boolean success; // 성공 여부
    private String code;     // 에러 코드 (성공 시 null 가능)
    private String message;  // 메시지
    private T data;          // 실제 데이터

    public static <T> TestGlobalResponseDTO<T> ok(T data) {
        return TestGlobalResponseDTO.<T>builder()
                .success(true)
                .code(null)
                .message("success")
                .data(data)
                .build();
    }

    public static <T> TestGlobalResponseDTO<T> ok(String message, T data) {
        return TestGlobalResponseDTO.<T>builder()
                .success(true)
                .message(message)
                .code("200")
                .data(data)
                .build();
    }

    public static <T> TestGlobalResponseDTO<T> ok(String message) {
        return TestGlobalResponseDTO.<T>builder()
                .success(true)
                .message(message)
                .code("200")
                .build();
    }

    // ===== 에러 응답 =====
    public static <T> TestGlobalResponseDTO<T> error(String message) {
        return TestGlobalResponseDTO.<T>builder()
                .success(false)
                .message(message)
                .code("ERROR")
                .data(null)
                .build();
    }

    public static <T> TestGlobalResponseDTO<T> error(String code, String message) {
    return TestGlobalResponseDTO.<T>builder()
            .success(false)
            .code(code)
            .message(message)
            .data(null)
            .build();
}
public static TestGlobalResponseDTO<Void> ok() {
        return TestGlobalResponseDTO.<Void>builder()
                .success(true)
                .code("SUCCESS")
                .message("요청이 성공적으로 처리되었습니다.")
                .build();
    }
}