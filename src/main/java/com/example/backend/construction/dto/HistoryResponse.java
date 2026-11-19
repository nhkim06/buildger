package com.example.backend.construction.dto;

import com.example.backend.construction.entity.RecordStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "정정 체인 이력")
public record HistoryResponse(
        @Schema(description = "최초 원본 기록 ID", example = "101") Long rootDataId,
        @Schema(description = "체인의 최신 유효 기록 ID (무효화된 경우 무효화 기록 ID)", example = "135") Long currentDataId,
        @Schema(description = "체인의 최종 상태 (ACTIVE·INVALIDATED)") RecordStatus currentStatus,
        @Schema(description = "체인 항목 (등록 순)") List<Item> items
) {

    @Schema(description = "정정 체인 항목")
    public record Item(
            @Schema(description = "기록 ID") Long dataId,
            @Schema(description = "ORIGINAL·AMENDMENT·INVALIDATION") String kind,
            @Schema(description = "제목") String title,
            @Schema(description = "기록 상태") RecordStatus status,
            @Schema(description = "서명 여부") boolean locked,
            @Schema(description = "정정·무효화 사유 (원본은 null)", nullable = true) String reason,
            @Schema(description = "작성자") Person createdBy,
            @Schema(description = "등록 시각") String createdAt
    ) {
    }

    @Schema(description = "작성자")
    public record Person(UUID uid, String name) {
    }
}
