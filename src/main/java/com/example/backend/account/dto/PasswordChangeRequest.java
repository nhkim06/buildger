package com.example.backend.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "비밀번호 변경 요청")
public record PasswordChangeRequest(
        @Schema(description = "현재 비밀번호", example = "Passw0rd!", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String currentPassword,

        @Schema(description = "새 비밀번호", example = "N3wPassw0rd!", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Pattern(regexp = AccountValidation.PASSWORD_PATTERN, message = AccountValidation.PASSWORD_MESSAGE)
        String newPassword
) {
}
