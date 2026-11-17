package com.example.backend.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 응답")
public record LoginResponse(
        @Schema(description = "토큰 타입", example = "Bearer") String tokenType,
        @Schema(description = "JWT Access Token") String accessToken,
        @Schema(description = "만료까지 남은 시간(초)", example = "7200") long expiresIn,
        @Schema(description = "로그인한 계정") AccountResponse account
) {
}
