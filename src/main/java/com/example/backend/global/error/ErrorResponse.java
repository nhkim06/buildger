package com.example.backend.global.error;

import com.example.backend.global.common.Times;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "공통 오류 응답")
public record ErrorResponse(
        @Schema(description = "오류 발생 시각 (UTC)", example = "2026-11-17T00:30:00Z")
        String timestamp,
        @Schema(description = "HTTP 상태 코드", example = "400")
        int status,
        @Schema(description = "애플리케이션 오류 코드", example = "INVALID_INPUT")
        String code,
        @Schema(description = "사용자용 메시지", example = "요청 값이 올바르지 않습니다.")
        String message,
        @Schema(description = "요청 URI", example = "/api/auth/signup")
        String path,
        @Schema(description = "필드 검증 오류 목록")
        List<FieldErrorDetail> errors
) {

    public static ErrorResponse of(ErrorCode code, String message, String path, List<FieldErrorDetail> errors) {
        return new ErrorResponse(Times.nowString(), code.getStatus().value(), code.name(),
                message, path, errors == null ? List.of() : errors);
    }

    @Schema(description = "필드 검증 오류")
    public record FieldErrorDetail(
            @Schema(description = "필드명", example = "loginId") String field,
            @Schema(description = "오류 사유") String reason) {
    }
}
