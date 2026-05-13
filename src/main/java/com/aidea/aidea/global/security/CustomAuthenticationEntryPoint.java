package com.aidea.aidea.global.security;

import com.aidea.aidea.global.dto.GlobalResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String uri = request.getRequestURI();
        String requestedWith = request.getHeader("X-Requested-With");

        if (uri.startsWith("/api/") || "XMLHttpRequest".equals(requestedWith)) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            objectMapper.writeValue(response.getWriter(),
                    GlobalResponse.error("UNAUTHORIZED", "인증이 필요합니다."));
        } else {
            // 비API 경로 (브라우저 직접 접근 등)는 OAuth2 시작점으로 리다이렉트
            response.sendRedirect("/oauth2/authorization/github");
        }
    }
}
