package com.aidea.aidea.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ===== 공통 =====
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."),

    // ===== 인증 (Auth) =====
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "유저를 찾을 수 없습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh Token이 유효하지 않습니다."),
    REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_MISMATCH", "Refresh Token이 일치하지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),

    // ===== 팀스페이스 (Teamspace) =====
    TEAMSPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "TEAMSPACE_NOT_FOUND", "팀스페이스를 찾을 수 없습니다."),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "ALREADY_MEMBER", "이미 팀스페이스에 가입된 회원입니다."),
    NOT_TEAMSPACE_OWNER(HttpStatus.FORBIDDEN, "NOT_TEAMSPACE_OWNER", "팀스페이스 소유자만 가능합니다."),
    NOT_TEAMSPACE_MEMBER(HttpStatus.FORBIDDEN, "NOT_TEAMSPACE_MEMBER", "팀스페이스 소속이 아닙니다."),
    INSUFFICIENT_PERMISSION(HttpStatus.FORBIDDEN, "INSUFFICIENT_PERMISSION", "권한이 없습니다."),

    // ===== 문서 (Document) =====
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "문서를 찾을 수 없습니다."),

    // ===== 초대 (Invitation) =====
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "초대를 찾을 수 없습니다."),
    INVITATION_EXPIRED(HttpStatus.BAD_REQUEST, "INVITATION_EXPIRED", "만료된 초대입니다.");

    private final HttpStatus httpStatus;  // HTTP 상태 코드 (404, 401 등)
    private final String code;            // 우리가 정한 에러 코드 문자열
    private final String message;         // 사용자에게 보여줄 메시지
}
