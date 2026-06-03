package com.aidea.aidea.global.config;

import com.aidea.aidea.global.security.CustomAuthenticationEntryPoint;
import com.aidea.aidea.global.security.jwt.JwtAuthenticationFilter;
import com.aidea.aidea.global.security.oauth2.CustomOAuth2UserService;
import com.aidea.aidea.global.security.oauth2.HttpCookieOAuth2AuthorizationRequestRepository;
import com.aidea.aidea.global.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.aidea.aidea.global.security.oauth2.OAuth2AuthenticationSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.aidea.aidea.global.security.oauth2.CustomAuthorizationRequestResolver;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2FailureHandler;
    private final HttpCookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final ClientRegistrationRepository clientRegistrationRepository;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/invitations/accept").permitAll()
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/api/auth/refresh",
                                "/ws/**",           // WebSocket — HandshakeInterceptor에서 인증 처리
                                "/favicon.ico",
                                "/error",
                                "/h2-console/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/invitations/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                )
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint ->
                                endpoint
                                        .authorizationRequestRepository(cookieAuthorizationRequestRepository)
                                        .authorizationRequestResolver(new CustomAuthorizationRequestResolver(clientRegistrationRepository)))
                        .userInfoEndpoint(userInfo ->
                                userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://localhost:5173",
                frontendUrl
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
