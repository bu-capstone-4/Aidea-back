package com.aidea.aidea.domain.teamspace.websocket;

import com.aidea.aidea.domain.teamspace.entity.TeamSpace;
import com.aidea.aidea.domain.teamspace.entity.TeamspaceMember;
import com.aidea.aidea.domain.teamspace.repository.TeamSpaceRepository;
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
public class TeamspaceHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final TeamSpaceRepository teamSpaceRepository;
    private final TeamspaceMemberRepository teamspaceMemberRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {

        String path = request.getURI().getPath();
        String teamspaceId = path.substring(path.lastIndexOf('/') + 1);

        log.debug("[WS-TS] handshake attempt teamspaceId={} remote={}", teamspaceId, request.getRemoteAddress());

        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            token = extractTokenFromCookie(request);
        }
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            log.warn("[WS-TS] handshake rejected teamspaceId={} reason=INVALID_TOKEN", teamspaceId);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(token);

        TeamSpace teamSpace = teamSpaceRepository.findById(teamspaceId).orElse(null);
        if (teamSpace == null) {
            log.warn("[WS-TS] handshake rejected userId={} teamspaceId={} reason=TEAMSPACE_NOT_FOUND", userId, teamspaceId);
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }

        Optional<TeamspaceMember> member = teamspaceMemberRepository
                .findByTeamspaceIdAndUserId(teamspaceId, userId);

        if (member.isEmpty()) {
            log.warn("[WS-TS] handshake rejected userId={} teamspaceId={} reason=NOT_TEAMSPACE_MEMBER", userId, teamspaceId);
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        attributes.put("teamspaceId", teamspaceId);
        attributes.put("userId", userId.toString());
        attributes.put("role", member.get().getRole());
        attributes.put("currentDocumentId", null);

        log.info("[WS-TS] handshake accepted userId={} teamspaceId={} role={}", userId, teamspaceId, member.get().getRole());
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
        return null;
    }

    private String extractTokenFromCookie(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) return null;
        Cookie[] cookies = servletRequest.getServletRequest().getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if ("access_token".equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
