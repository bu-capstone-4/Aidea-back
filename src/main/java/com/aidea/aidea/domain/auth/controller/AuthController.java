package com.aidea.aidea.domain.auth.controller;

import com.aidea.aidea.domain.auth.dto.response.UserResponse;
import com.aidea.aidea.domain.auth.service.AuthService;
import com.aidea.aidea.global.dto.GlobalResponse;
import com.aidea.aidea.global.exception.CustomException;
import com.aidea.aidea.global.exception.ErrorCode;
import com.aidea.aidea.global.util.CookieUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieUtils cookieUtils;

    @Operation(summary = "토큰 갱신")
    @PostMapping("/refresh")
    public ResponseEntity<GlobalResponse<Void>> refresh(HttpServletRequest request,
                                                     HttpServletResponse response) {
        String refreshToken = cookieUtils.extractCookieValue(request, "refresh_token")
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REFRESH_TOKEN));
        String newAccessToken = authService.refreshToken(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtils.createAccessTokenCookie(newAccessToken).toString());
        return ResponseEntity.ok(GlobalResponse.ok("토큰이 갱신되었습니다."));
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ResponseEntity<GlobalResponse<Void>> logout(HttpServletRequest request,
                                                    HttpServletResponse response,
                                                    @AuthenticationPrincipal String userId) {
        authService.logout(Long.parseLong(userId));

        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtils.expireCookie("access_token").toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtils.expireCookie("refresh_token").toString());
        response.sendRedirect("https://github.com/logout");
    }

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public ResponseEntity<GlobalResponse<UserResponse>> me(@AuthenticationPrincipal String userId) {
        UserResponse user = authService.getCurrentUser(Long.parseLong(userId));
        return ResponseEntity.ok(GlobalResponse.ok(user));
    }
}
