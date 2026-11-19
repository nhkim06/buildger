package com.example.backend.project.dto;

import com.example.backend.account.entity.Account;
import com.example.backend.project.entity.ProjectRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "프로젝트 참여자 (전화번호 미포함)")
public record ProjectMemberResponse(
        @Schema(description = "사용자 ID") UUID uid,
        @Schema(description = "로그인 ID", example = "kim.site01") String loginId,
        @Schema(description = "이름", example = "김현장") String name,
        @Schema(description = "소속기관", nullable = true) String organization,
        @Schema(description = "계정 역할 값", example = "1") int roleLevel,
        @Schema(description = "계정 역할 코드", example = "FIELD_WORKER") String roleName,
        @Schema(description = "프로젝트 역할", example = "MEMBER") ProjectRole projectRole,
        @Schema(description = "참여 시각 (OWNER는 프로젝트 생성 시각)", example = "2026-11-17T00:35:00Z") String addedAt
) {

    public static ProjectMemberResponse of(Account account, ProjectRole projectRole, String addedAt) {
        return new ProjectMemberResponse(account.getUid(), account.getLoginId(), account.getName(),
                account.getOrganization(), account.getRoleLevel(), account.getRole().name(), projectRole, addedAt);
    }
}
