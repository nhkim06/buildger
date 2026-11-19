package com.example.backend.confirmation.dto;

import com.example.backend.account.entity.RoleLevel;
import com.example.backend.construction.entity.Confirmation;
import com.example.backend.construction.entity.ConfirmationState;
import com.example.backend.file.dto.FileResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "확인·서명 이력")
public record ConfirmationResponse(
        @Schema(description = "확인 ID") String confirmationId,
        @Schema(description = "확인자 (서명 당시 값)") Confirmer confirmer,
        @Schema(description = "APPROVED / REJECTED") ConfirmationState status,
        @Schema(description = "의견", nullable = true) String comment,
        @Schema(description = "서명 이미지") Signature signature,
        @Schema(description = "서명 시점 기록 데이터 해시 (SHA-256)") String recordSnapshotSha256,
        @Schema(description = "확인 시각 (서버 시각)") String confirmedAt
) {

    public static ConfirmationResponse from(Confirmation c) {
        Integer level = c.getConfirmerRoleLevel();
        Confirmer confirmer = new Confirmer(
                c.getConfirmerUid() == null ? null : UUID.fromString(c.getConfirmerUid()),
                c.getConfirmerName(), c.getConfirmerOrganization(), level,
                level == null ? null : RoleLevel.fromLevel(level).name());
        Signature signature = c.getSignature() == null ? null : new Signature(
                c.getSignature().getStorageId(), FileResponse.downloadUrl(c.getSignature().getStorageId()),
                c.getSignature().getSha256());
        return new ConfirmationResponse(c.getConfirmationId(), confirmer, c.getStatus(), c.getComment(), signature,
                c.getRecordSnapshotSha256(), c.getConfirmedAt());
    }

    @Schema(description = "확인자")
    public record Confirmer(UUID uid, String name, String organization, Integer roleLevel, String roleName) {
    }

    @Schema(description = "서명 이미지")
    public record Signature(Long storageId, String downloadUrl, String sha256) {
    }
}
