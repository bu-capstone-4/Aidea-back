package com.aidea.aidea.domain.auth.controller;

import com.aidea.aidea.domain.auth.dto.response.UserResponse;
import com.aidea.aidea.domain.auth.service.AuthService;
import com.aidea.aidea.global.dto.TestGlobalResponseDTO;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import com.aidea.aidea.global.util.CookieUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieUtils cookieUtils;

    @Operation(summary = "토큰 갱신")
    @PostMapping("/refresh")
    public ResponseEntity<TestGlobalResponseDTO<String>> refresh(HttpServletRequest request,
                                                               HttpServletResponse response) {
        String refreshToken = cookieUtils.extractCookieValue(request, "refresh_token")
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REFRESH_TOKEN));
        String newAccessToken = authService.refreshToken(refreshToken);
        response.addCookie(cookieUtils.createAccessTokenCookie(newAccessToken));
        return ResponseEntity.ok(TestGlobalResponseDTO.ok(null, "토큰이 갱신되었습니다."));
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ResponseEntity<TestGlobalResponseDTO<String>> logout(HttpServletRequest request,
                                                    HttpServletResponse response) {
        String userId = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        authService.logout(Long.parseLong(userId));

        response.addCookie(cookieUtils.expireCookie("access_token"));
        response.addCookie(cookieUtils.expireCookie("refresh_token"));
        return ResponseEntity.ok(TestGlobalResponseDTO.ok(null, "로그아웃 성공"));
    }

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public ResponseEntity<TestGlobalResponseDTO<UserResponse>> me() {
        String userId = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        UserResponse user = authService.getCurrentUser(Long.parseLong(userId));
        return ResponseEntity.ok(TestGlobalResponseDTO.ok(user));
    }
}
