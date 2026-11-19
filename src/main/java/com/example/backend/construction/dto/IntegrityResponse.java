package com.example.backend.construction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "서명 해시 검증 결과 (docs/api.md 4.4)")
public record IntegrityResponse(
        @Schema(description = "NOT_SIGNED / VERIFIED / MISMATCH") IntegrityStatus status,
        @Schema(description = "최초 서명의 해시", nullable = true) String signedSnapshotSha256,
        @Schema(description = "현재 데이터로 다시 계산한 해시 (서명 없으면 null)", nullable = true) String currentSnapshotSha256,
        @Schema(description = "이미지 바이너리 해시 검증 결과 (verifyFiles=true일 때만 검사)") FileIntegrityStatus fileStatus,
        @Schema(description = "검증 시각") String verifiedAt
) {

    public enum IntegrityStatus {
        NOT_SIGNED,
        VERIFIED,
        MISMATCH
    }

    public enum FileIntegrityStatus {
        NOT_CHECKED,
        VERIFIED,
        MISMATCH
    }
}
