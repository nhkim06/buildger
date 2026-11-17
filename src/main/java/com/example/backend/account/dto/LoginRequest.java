package com.example.backend.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그인 요청")
public record LoginRequest(
        @Schema(description = "로그인 ID", example = "kim.site01", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String loginId,

        @Schema(description = "비밀번호", example = "Passw0rd!", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String password
) {
}
