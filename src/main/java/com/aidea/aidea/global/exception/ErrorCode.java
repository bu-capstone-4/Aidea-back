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
    INVITATION_EXPIRED(HttpStatus.BAD_REQUEST, "INVITATION_EXPIRED", "만료된 초대입니다."),
    INVITATION_EMAIL_MISMATCH(HttpStatus.FORBIDDEN, "INVITATION_EMAIL_MISMATCH", "초대받은 이메일 계정으로 로그인하세요."),
    ALREADY_INVITED(HttpStatus.CONFLICT, "ALREADY_INVITED", "이미 초대된 이메일입니다."),
    INVITATION_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "INVITATION_001", "한 번에 최대 8명까지 초대할 수 있습니다."),

    // ===== AI 피드백 (Feedback) =====
    FEEDBACK_NOT_FOUND(HttpStatus.NOT_FOUND, "FEEDBACK_NOT_FOUND", "피드백을 찾을 수 없습니다."),
    FEEDBACK_IN_PROGRESS(HttpStatus.CONFLICT, "FEEDBACK_IN_PROGRESS", "이미 진행 중인 피드백이 있습니다."),
    FEEDBACK_INVALID_STATUS(HttpStatus.BAD_REQUEST, "FEEDBACK_INVALID_STATUS", "현재 상태에서는 해당 작업을 수행할 수 없습니다."),

    // ===== 백로그 (Backlog) =====
    EPIC_NOT_FOUND(HttpStatus.NOT_FOUND, "EPIC_NOT_FOUND", "에픽을 찾을 수 없습니다."),
    STORY_NOT_FOUND(HttpStatus.NOT_FOUND, "STORY_NOT_FOUND", "스토리를 찾을 수 없습니다."),
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "태스크를 찾을 수 없습니다."),
    BACKLOG_TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "BACKLOG_TASK_NOT_FOUND", "최상위 태스크를 찾을 수 없습니다."),
    BACKLOG_CONFIG_FIELD_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "BACKLOG_CONFIG_FIELD_NOT_ALLOWED", "백로그 설정에서 비활성화된 필드입니다."),
    BACKLOG_DRAFT_NOT_FIRST_CREATION(HttpStatus.CONFLICT, "BACKLOG_DRAFT_NOT_FIRST_CREATION", "백로그 설정이 이미 존재하여 초안을 생성할 수 없습니다."),
    BACKLOG_DRAFT_BLOCKED_BY_DOCUMENT_DRAFT(HttpStatus.CONFLICT, "BACKLOG_DRAFT_BLOCKED_BY_DOCUMENT_DRAFT", "AI 생성 대기 중인 문서가 있어 백로그 초안을 생성할 수 없습니다."),
    BACKLOG_DRAFT_NO_PLANNING_DOCUMENT(HttpStatus.BAD_REQUEST, "BACKLOG_DRAFT_NO_PLANNING_DOCUMENT", "백로그 초안을 생성할 기획 문서 내용이 없습니다."),
    BACKLOG_DRAFT_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "BACKLOG_DRAFT_ALREADY_IN_PROGRESS", "이미 백로그 초안 생성이 진행 중입니다."),
    BACKLOG_DRAFT_GEMINI_INVALID_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, "BACKLOG_DRAFT_GEMINI_INVALID_RESPONSE", "AI가 올바른 응답을 반환하지 않았습니다."),
    BACKLOG_DRAFT_GEMINI_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "BACKLOG_DRAFT_GEMINI_API_ERROR", "AI API 호출 중 오류가 발생했습니다."),
    BACKLOG_DRAFT_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "BACKLOG_DRAFT_GENERATION_FAILED", "백로그 초안 생성 중 예기치 않은 오류가 발생했습니다."),

    // ===== 초안 (Draft) =====
    DRAFT_NOT_FOUND(HttpStatus.NOT_FOUND, "DRAFT_NOT_FOUND", "초안을 찾을 수 없습니다."),
    DRAFT_INVALID_STATUS(HttpStatus.BAD_REQUEST, "DRAFT_INVALID_STATUS", "현재 상태에서는 해당 작업을 수행할 수 없습니다."),
    DRAFT_IN_PROGRESS(HttpStatus.CONFLICT, "DRAFT_IN_PROGRESS", "초안 생성 중에는 피드백을 요청할 수 없습니다."),
    DRAFT_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "DRAFT_ALREADY_IN_PROGRESS", "이미 초안 생성 중입니다."),
    DRAFT_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "DRAFT_QUOTA_EXCEEDED", "AI API 요청 한도를 초과했습니다."),
    DRAFT_GEMINI_INVALID_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, "DRAFT_GEMINI_INVALID_RESPONSE", "AI가 올바른 응답을 반환하지 않았습니다."),
    DRAFT_GEMINI_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "DRAFT_GEMINI_API_ERROR", "AI API 호출 중 오류가 발생했습니다."),
    DRAFT_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "DRAFT_GENERATION_FAILED", "초안 생성 중 예기치 않은 오류가 발생했습니다.");

    private final HttpStatus httpStatus;  // HTTP 상태 코드 (404, 401 등)
    private final String code;            // 우리가 정한 에러 코드 문자열
    private final String message;         // 사용자에게 보여줄 메시지
}
