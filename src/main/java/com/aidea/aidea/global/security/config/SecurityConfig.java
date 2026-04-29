package com.aidea.aidea.global.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            // ⚠️ REST API에서는 CSRF 비활성화 (필수)
            .csrf(csrf -> csrf.disable())

            // ⚠️ 세션 사용 안함 (JWT 방식 기준)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 🔐 URL 접근 권한 설정
            .authorizeHttpRequests(auth -> auth

                // 로그인 / 회원가입 / 테스트 API는 허용
                .requestMatchers(
                    "/auth/**",
                    "/api/documents/**",
                    "/h2-console/**"
                ).permitAll()

                // 나머지는 인증 필요
                .anyRequest().authenticated()
            );

        // H2 콘솔 사용 시 필요 (개발용)
        http.headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }
}