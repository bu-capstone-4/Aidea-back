package com.aidea.aidea.domain.documents.websocket;

import com.aidea.aidea.domain.teamspace.websocket.TeamspaceHandshakeInterceptor;
import com.aidea.aidea.domain.teamspace.websocket.TeamspaceWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final DocumentWebSocketHandler documentWebSocketHandler;
    private final DocumentHandshakeInterceptor documentHandshakeInterceptor;
    private final TeamspaceWebSocketHandler teamspaceWebSocketHandler;
    private final TeamspaceHandshakeInterceptor teamspaceHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(documentWebSocketHandler, "/ws/documents/{docId}")
                .setAllowedOrigins("*")
                .addInterceptors(documentHandshakeInterceptor);

        registry.addHandler(teamspaceWebSocketHandler, "/ws/teamspace/{teamspaceId}")
                .setAllowedOrigins("*")
                .addInterceptors(teamspaceHandshakeInterceptor);
    }
}
