package com.example.backend.construction.dto;

import com.example.backend.construction.entity.CorrectionKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "정정·무효화 연결 정보")
public record CorrectionResponse(
        @Schema(description = "AMENDMENT(정정) / INVALIDATION(무효화)") CorrectionKind kind,
        @Schema(description = "정정·무효화 대상 기록", example = "101") Long originalDataId,
        @Schema(description = "정정 체인의 최초 원본 기록", example = "101") Long rootDataId,
        @Schema(description = "사유") String reason,
        @Schema(description = "작성자") Person correctedBy,
        @Schema(description = "작성 시각") String correctedAt
) {

    @Schema(description = "작성자")
    public record Person(UUID uid, String name) {
    }
}
