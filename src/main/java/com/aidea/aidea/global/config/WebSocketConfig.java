package com.aidea.aidea.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;




@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer{

    // 메시지 라우팅 규칙
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 클라이언트가 구독할 prefix
        // ex) /topic/doc/doc-001
        registry.enableSimpleBroker("/topic", "/queue");

        // 클라이언트가 서버로 메시지 보낼 때 prefix
        // ex) /app/doc/doc-001/update
        registry.setApplicationDestinationPrefixes("/app");
    }

    // 소켓 엔드포인트 설정
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }

    // 소켓 크기 설정
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {

        registry.setMessageSizeLimit(512 * 1024); // 512KB
        registry.setSendBufferSizeLimit(512 * 1024); // 512KB
        registry.setSendTimeLimit(20 * 1000); // (선택) 20초
    }
}