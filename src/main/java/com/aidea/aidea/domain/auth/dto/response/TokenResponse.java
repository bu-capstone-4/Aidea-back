package com.aidea.aidea.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "토큰 응답")
public class TokenResponse {

    @Schema(description = "Access Token (API 호출 시 사용)", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;

    @Schema(description = "Refresh Token (Access Token 갱신 시 사용)", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String refreshToken;
}
