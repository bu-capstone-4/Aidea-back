package com.aidea.aidea.global.websocket;

import com.aidea.aidea.domain.documents.websocket.DocumentHandshakeInterceptor;
import com.aidea.aidea.domain.documents.websocket.DocumentWebSocketHandler;
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

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(documentWebSocketHandler, "/ws/documents/{docId}")
                .setAllowedOrigins("*")
                .addInterceptors(documentHandshakeInterceptor);
    }
}
