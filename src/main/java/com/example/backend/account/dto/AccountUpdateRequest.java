package com.example.backend.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "내 계정 수정 요청 (전달된 필드만 수정)")
public record AccountUpdateRequest(
        @Schema(description = "이름 (공백 불가)", example = "김현장")
        @Size(min = 1, max = 100)
        @Pattern(regexp = "(?s).*\\S.*", message = "이름은 공백일 수 없습니다.")
        String name,

        @Schema(description = "소속기관 (빈 문자열이면 삭제)", example = "OO건설 품질팀")
        @Size(max = 255)
        String organization,

        @Schema(description = "전화번호 (빈 문자열이면 삭제)", example = "010-9876-5432")
        @Size(max = 20)
        @Pattern(regexp = AccountValidation.PHONE_PATTERN, message = "숫자와 -만 사용할 수 있습니다.")
        String phone
) {

    public boolean hasNoChanges() {
        return name == null && organization == null && phone == null;
    }
}
