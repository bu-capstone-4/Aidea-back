package com.aidea.aidea.global.security.jwt;

import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.global.util.CookieUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CookieUtils cookieUtils;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String accessToken = resolveAccessToken(request);

        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            setAuthentication(accessToken);
        } else {
            tryRefreshToken(request, response);
        }

        filterChain.doFilter(request, response);
    }

    private void tryRefreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieUtils.extractCookieValue(request, "refresh_token").orElse(null);
        if (refreshToken == null || !jwtTokenProvider.validateToken(refreshToken)) return;

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !refreshToken.equals(user.getRefreshToken())) return;

        String newAccessToken = jwtTokenProvider.createAccessToken(userId);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtils.createAccessTokenCookie(newAccessToken).toString());
        setAuthentication(newAccessToken);
    }

    private void setAuthentication(String token) {
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId.toString(), null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String cookieToken = cookieUtils.extractCookieValue(request, "access_token").orElse(null);
        if (cookieToken != null) return cookieToken;

        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }
}
