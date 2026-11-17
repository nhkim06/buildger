package com.example.backend.account.dto;

import com.example.backend.account.entity.Account;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "계정 정보")
public record AccountResponse(
        @Schema(description = "사용자 ID", example = "8a6e0804-2bd0-4672-b79d-d97027f9071a") UUID uid,
        @Schema(description = "로그인 ID", example = "kim.site01") String loginId,
        @Schema(description = "이름", example = "김현장") String name,
        @Schema(description = "계정 역할 값", example = "1") int roleLevel,
        @Schema(description = "계정 역할 코드", example = "FIELD_WORKER") String roleName,
        @Schema(description = "소속기관", example = "OO건설", nullable = true) String organization,
        @Schema(description = "전화번호", example = "010-1234-5678", nullable = true) String phone
) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getUid(), account.getLoginId(), account.getName(),
                account.getRoleLevel(), account.getRole().name(), account.getOrganization(), account.getPhone());
    }
}
