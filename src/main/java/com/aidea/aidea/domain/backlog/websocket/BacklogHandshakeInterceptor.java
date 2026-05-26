package com.aidea.aidea.domain.backlog.websocket;

import com.aidea.aidea.domain.auth.entity.User;
import com.aidea.aidea.domain.auth.repository.UserRepository;
import com.aidea.aidea.domain.teamspace.entity.TeamspaceMember;
import com.aidea.aidea.domain.teamspace.repository.TeamspaceMemberRepository;
import com.aidea.aidea.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class BacklogHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final TeamspaceMemberRepository teamspaceMemberRepository;
    private final UserRepository userRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {

        String path = request.getURI().getPath();
        String teamspaceId = path.substring(path.lastIndexOf('/') + 1);

        log.debug("[WS-BACKLOG] handshake attempt teamspaceId={} remote={}", teamspaceId, request.getRemoteAddress());

        String token = extractToken(request);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            log.warn("[WS-BACKLOG] handshake rejected teamspaceId={} reason=INVALID_TOKEN", teamspaceId);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(token);

        Optional<TeamspaceMember> memberOpt =
                teamspaceMemberRepository.findByTeamspaceIdAndUserId(teamspaceId, userId);
        if (memberOpt.isEmpty()) {
            log.warn("[WS-BACKLOG] handshake rejected userId={} teamspaceId={} reason=NOT_TEAMSPACE_MEMBER", userId, teamspaceId);
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        User user = userRepository.findById(userId).orElse(null);

        attributes.put("teamSpaceId", teamspaceId);
        attributes.put("userId", userId.toString());
        attributes.put("role", memberOpt.get().getRole());
        attributes.put("userName", user != null ? user.getName() : "");
        attributes.put("profileImageUrl", user != null && user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "");

        log.info("[WS-BACKLOG] handshake accepted userId={} teamspaceId={} role={}", userId, teamspaceId, memberOpt.get().getRole());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}

    private String extractToken(ServerHttpRequest request) {
        List<String> authHeaders = request.getHeaders().get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String bearer = authHeaders.get(0);
            if (bearer.startsWith("Bearer ")) return bearer.substring(7);
        }
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) return param.substring(6);
            }
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            Cookie[] cookies = servletRequest.getServletRequest().getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("access_token".equals(cookie.getName())) return cookie.getValue();
                }
            }
        }
        return null;
    }
}
