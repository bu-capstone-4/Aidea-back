package com.aidea.aidea.domain.documents.websocket;

import com.aidea.aidea.domain.documents.entity.Document;
import com.aidea.aidea.domain.documents.repository.DocumentRepository;
import com.aidea.aidea.domain.teamspace.entity.TeamspaceMember;
import com.aidea.aidea.domain.teamspace.repository.TeamspaceMemberRepository;
import com.aidea.aidea.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

        // 1. URL에서 docId 추출
        String path = request.getURI().getPath();
        String docId = path.substring(path.lastIndexOf('/') + 1);

        // 2. Authorization 헤더 또는 쿼리 파라미터에서 JWT 추출
        String token = extractToken(request);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(token);

        // 3. 문서 존재 확인
        Document doc = documentRepository.findById(docId).orElse(null);
        if (doc == null) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }

        // 4. 팀스페이스 소속 여부 확인 (VIEWER 포함 소속이면 연결 허용)
        Optional<TeamspaceMember> member = teamspaceMemberRepository
                .findByTeamspaceIdAndUserId(doc.getTeamspace().getId(), userId);

        if (member.isEmpty()) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        // 5. 핸들러에서 사용할 정보 저장
        attributes.put("docId", docId);
        attributes.put("userId", userId.toString());
        attributes.put("role", member.get().getRole());

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}

    private String extractToken(ServerHttpRequest request) {
        // Authorization 헤더 먼저 확인
        List<String> authHeaders = request.getHeaders().get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String bearer = authHeaders.get(0);
            if (bearer.startsWith("Bearer ")) return bearer.substring(7);
        }
        // 쿼리 파라미터 fallback: ?token=xxx (WebSocket은 헤더 설정이 어려운 경우 있음)
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) return param.substring(6);
            }
        }
        return null;
    }
}
