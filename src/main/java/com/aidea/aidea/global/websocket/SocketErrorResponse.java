package com.aidea.aidea.global.websocket;

import lombok.Getter;

/** 소켓 에러 페이로드: {"event":"error","code":"...","message":"..."} */
@Getter
public class SocketErrorResponse {

    private final String event = "error";
    private final String code;
    private final String message;

    private SocketErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public static SocketErrorResponse of(SocketErrorCode errorCode) {
        return new SocketErrorResponse(errorCode.getCode(), errorCode.getMessage());
    }
}
