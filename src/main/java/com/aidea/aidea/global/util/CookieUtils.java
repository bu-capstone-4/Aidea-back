package com.aidea.aidea.global.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

@Component
public class CookieUtils {

    @Value("${app.cookie.secure}")
    private boolean secure;

    private ResponseCookie buildCookie(String name, String value, int maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(maxAge)
                // 프론트(pages.dev)와 백엔드(duckdns.org)가 다른 도메인이므로
                // cross-site 요청에서도 쿠키가 전송되도록 SameSite=None 설정.
                // 개발 환경(secure=false)에서는 Lax 사용 (SameSite=None은 Secure 필수)
                .sameSite(secure ? "None" : "Lax")
                .build();
    }

    public ResponseCookie createAccessTokenCookie(String token) {
        return buildCookie("access_token", token, 1800);
    }

    public ResponseCookie createRefreshTokenCookie(String token) {
        return buildCookie("refresh_token", token, 1209600);
    }

    public ResponseCookie expireCookie(String name) {
        return buildCookie(name, "", 0);
    }

    public Optional<String> extractCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
