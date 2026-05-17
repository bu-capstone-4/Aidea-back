package com.aidea.aidea.global.websocket;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SocketErrorCode {

    // ===== 문서 실시간 편집 =====
    INSUFFICIENT_PERMISSION("INSUFFICIENT_PERMISSION", "이 작업을 수행할 권한이 없습니다.", false),
    DOCUMENT_NOT_FOUND("DOCUMENT_NOT_FOUND", "문서를 찾을 수 없습니다.", false),
    INVALID_MESSAGE("INVALID_MESSAGE", "잘못된 요청입니다.", false),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", false),

    // ===== 인증 (세션 종료 필요) =====
    UNAUTHORIZED("UNAUTHORIZED", "인증이 만료되었습니다. 다시 로그인해 주세요.", true),
    SESSION_EXPIRED("SESSION_EXPIRED", "세션이 만료되었습니다. 다시 로그인해 주세요.", true);

    private final String code;
    private final String message;
    /** true이면 에러 전송 후 세션을 닫는다 */
    private final boolean fatal;
}
