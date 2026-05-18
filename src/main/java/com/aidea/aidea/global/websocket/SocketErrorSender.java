package com.aidea.aidea.global.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/**
 * 모든 소켓 핸들러에서 공통으로 사용하는 에러 전송 유틸리티.
 * fatal 에러(UNAUTHORIZED, SESSION_EXPIRED)는 에러 메시지 전송 후 세션을 닫는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocketErrorSender {

    private final ObjectMapper objectMapper;

    public void send(WebSocketSession session, SocketErrorCode errorCode) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(SocketErrorResponse.of(errorCode));
            session.sendMessage(new TextMessage(json));

            if (errorCode.isFatal()) {
                session.close(CloseStatus.POLICY_VIOLATION);
            }
        } catch (IOException e) {
            log.warn("[WS] failed to send error code={} sessionId={}", errorCode.getCode(), session.getId());
        }
    }
}
