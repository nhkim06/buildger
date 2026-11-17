package com.example.backend.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 요청")
public record SignupRequest(
        @Schema(description = "로그인 ID (4~100자, 영문·숫자·._-)", example = "kim.site01", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(min = 4, max = 100)
        @Pattern(regexp = AccountValidation.LOGIN_ID_PATTERN, message = "영문, 숫자, 일부 특수문자(._-)만 사용할 수 있습니다.")
        String loginId,

        @Schema(description = "비밀번호 (8~64자, 영문·숫자·특수문자 각 1자 이상)", example = "Passw0rd!", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Pattern(regexp = AccountValidation.PASSWORD_PATTERN, message = AccountValidation.PASSWORD_MESSAGE)
        String password,

        @Schema(description = "이름", example = "김현장", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 100)
        String name,

        @Schema(description = "계정 역할 (1: 현장직, 2: 시공사, 3: 시행사)", example = "1", allowableValues = {"1", "2", "3"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Min(1)
        @Max(3)
        Integer roleLevel,

        @Schema(description = "소속기관", example = "OO건설")
        @Size(max = 255)
        String organization,

        @Schema(description = "전화번호", example = "010-1234-5678")
        @Size(max = 20)
        @Pattern(regexp = AccountValidation.PHONE_PATTERN, message = "숫자와 -만 사용할 수 있습니다.")
        String phone
) {
}
