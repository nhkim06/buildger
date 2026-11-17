package com.example.backend.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 애플리케이션 오류 코드 (docs/api.md 15장).
 */
@Getter
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    SAME_AS_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "새 비밀번호가 현재 비밀번호와 같습니다."),
    FILE_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "기록당 사진 개수를 초과했습니다."),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "올바른 이미지 파일이 아닙니다."),
    MEMBER_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "프로젝트 참여자 수를 초과했습니다."),
    CONFIRMATION_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "확인·서명 건수를 초과했습니다."),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "인증 토큰이 만료되었습니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "로그인 ID 또는 비밀번호가 올바르지 않습니다."),
    INVALID_CURRENT_PASSWORD(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 올바르지 않습니다."),

    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    ROLE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "계정 역할상 허용되지 않은 작업입니다."),
    PROJECT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "프로젝트 참여자가 아닙니다."),
    INSUFFICIENT_PROJECT_ROLE(HttpStatus.FORBIDDEN, "프로젝트 권한이 부족합니다."),

    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "계정을 찾을 수 없습니다."),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트 참여자를 찾을 수 없습니다."),
    RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "시공 기록을 찾을 수 없습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
    AI_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 분석 결과를 찾을 수 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP Method입니다."),

    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 로그인 ID입니다."),
    ALREADY_PROJECT_MEMBER(HttpStatus.CONFLICT, "이미 프로젝트에 참여 중인 사용자입니다."),
    CANNOT_MODIFY_OWNER(HttpStatus.CONFLICT, "프로젝트 소유자는 변경하거나 제외할 수 없습니다."),
    RECORD_LOCKED(HttpStatus.CONFLICT, "서명된 기록은 변경할 수 없습니다."),
    RECORD_NOT_LOCKED(HttpStatus.CONFLICT, "서명 전 기록은 직접 수정하거나 삭제해야 합니다."),
    RECORD_ALREADY_CORRECTED(HttpStatus.CONFLICT, "이미 정정된 기록입니다."),
    RECORD_SUPERSEDED(HttpStatus.CONFLICT, "정정된 이전 버전 기록입니다."),
    RECORD_INVALIDATED(HttpStatus.CONFLICT, "무효화된 기록입니다."),
    RECORD_HAS_CORRECTIONS(HttpStatus.CONFLICT, "정정·무효화 기록이 참조 중인 기록입니다."),
    RECORD_HAS_NO_FILES(HttpStatus.CONFLICT, "사진이 없는 기록은 확인할 수 없습니다."),
    INVALID_RECORD_TYPE(HttpStatus.CONFLICT, "해당 기록 유형에는 허용되지 않는 작업입니다."),
    SELF_CONFIRMATION_NOT_ALLOWED(HttpStatus.CONFLICT, "본인이 작성한 기록은 확인할 수 없습니다."),
    DUPLICATE_CONFIRMATION(HttpStatus.CONFLICT, "직전 확인과 같은 결과로 다시 확인할 수 없습니다."),
    DUPLICATE_FILE(HttpStatus.CONFLICT, "같은 이미지가 이미 첨부되어 있습니다."),
    INTEGRITY_MISMATCH(HttpStatus.CONFLICT, "저장된 해시와 현재 데이터가 일치하지 않습니다."),
    NO_ANALYZABLE_FILES(HttpStatus.CONFLICT, "분석할 이미지가 없습니다."),
    AI_ANALYSIS_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "AI 분석 이력 건수를 초과했습니다."),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "다른 요청이 처리 중입니다. 잠시 후 다시 시도해 주세요."),

    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "파일 크기가 허용 범위를 초과했습니다."),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "요청 본문 크기가 허용 범위를 초과했습니다."),

    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "허용되지 않은 파일 형식입니다. JPEG 또는 PNG 이미지만 업로드할 수 있습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    AI_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "AI 서버 오류가 발생했습니다."),
    AI_SERVER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 서버 응답 시간이 초과되었습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
