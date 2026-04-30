package com.aidea.aidea.global.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Collections;

@Component
@RequiredArgsConstructor
//요청 1개 당 1번만 필터 실행
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        //토큰 꺼내기
        String token = resolveToken(request);

        //토큰이 있고, 유효한지 확인
        if (token != null && jwtTokenProvider.validateToken(token)) {
            //토큰에서 userId 꺼내기
            Long  userId = jwtTokenProvider.getUserIdFromToken(token);

            //Spring Security한테 "이 사람 인증됨" 알리기
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId.toString(),        //누구인지 (principal)
                            null,                     // 비밀번호 (소셜 로그인 -> null)
                            Collections.emptyList()   // 권한 목록 (지금은 비었음)
                    );

            //여기에 인증된 유저 set하면 이후에 controller에서 꺼낼 수 있음
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        //다음 단계로 넘기기
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); //7글자 뒤에 토큰만 잘라
        }
        return null; //토큰이 없으면 null 반환
    }
}
