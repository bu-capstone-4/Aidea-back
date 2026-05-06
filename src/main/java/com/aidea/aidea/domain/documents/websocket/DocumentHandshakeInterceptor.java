package com.aidea.aidea.domain.documents.websocket;

import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.teamspace.entity.TeamspaceMember;
import com.aidea.aidea.domain.teamspace.repository.TeamspaceMemberRepository;
import com.aidea.aidea.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final DocumentRepository documentRepository;
    private final TeamspaceMemberRepository teamspaceMemberRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {

        String path = request.getURI().getPath();
        String docId = path.substring(path.lastIndexOf('/') + 1);

        log.debug("[WS] handshake attempt docId={} remote={}", docId, request.getRemoteAddress());

        String token = extractToken(request);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            log.warn("[WS] handshake rejected docId={} reason=INVALID_TOKEN", docId);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(token);

        Document doc = documentRepository.findById(docId).orElse(null);
        if (doc == null) {
            log.warn("[WS] handshake rejected userId={} docId={} reason=DOCUMENT_NOT_FOUND", userId, docId);
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }

        Optional<TeamspaceMember> member = teamspaceMemberRepository
                .findByTeamspaceIdAndUserId(doc.getTeamspace().getId(), userId);

        if (member.isEmpty()) {
            log.warn("[WS] handshake rejected userId={} docId={} reason=NOT_TEAMSPACE_MEMBER", userId, docId);
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        attributes.put("docId", docId);
        attributes.put("userId", userId.toString());
        attributes.put("role", member.get().getRole());

        log.info("[WS] handshake accepted userId={} docId={} role={}", userId, docId, member.get().getRole());
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
}
