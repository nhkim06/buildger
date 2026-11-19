package com.example.backend.project.dto;

import com.example.backend.project.entity.ProjectRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "참여자 권한 변경 요청")
public record MemberRoleUpdateRequest(
        @Schema(description = "변경할 역할 (OWNER 불가)", example = "VIEWER", allowableValues = {"MANAGER", "MEMBER", "VIEWER"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        ProjectRole projectRole
) {
}
