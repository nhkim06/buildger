package com.example.backend.project.dto;

import com.example.backend.project.entity.ProjectRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "참여자 추가 요청")
public record MemberAddRequest(
        @Schema(description = "추가할 사용자의 로그인 ID", example = "kim.site01", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String loginId,

        @Schema(description = "부여할 역할 (OWNER 불가)", example = "MEMBER", allowableValues = {"MANAGER", "MEMBER", "VIEWER"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        ProjectRole projectRole
) {
}
