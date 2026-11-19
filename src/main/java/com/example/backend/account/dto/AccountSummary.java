package com.example.backend.account.dto;

import com.example.backend.account.entity.Account;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "사용자 요약")
public record AccountSummary(
        @Schema(description = "사용자 ID", example = "1f0e3c2a-6b7d-4c1e-9a2b-3c4d5e6f7a8b") UUID uid,
        @Schema(description = "이름", example = "이시공") String name,
        @Schema(description = "소속기관", example = "OO건설", nullable = true) String organization
) {

    public static AccountSummary from(Account account) {
        return account == null ? null : new AccountSummary(account.getUid(), account.getName(), account.getOrganization());
    }
}
