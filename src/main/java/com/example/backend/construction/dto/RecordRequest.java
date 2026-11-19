package com.example.backend.construction.dto;

import com.example.backend.construction.entity.RecordType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

@Schema(description = "시공 기록 생성 요청")
public record RecordRequest(
        @Schema(description = "기록 유형 (INVALIDATION 불가)", example = "PHOTO", allowableValues = {"PHOTO", "INSPECTION", "NOTE"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        RecordType type,

        @Schema(description = "제목", example = "3층 슬래브 철근 배근", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 200)
        String title,

        @Schema(description = "내용", example = "D13 @200 배근 완료")
        @Size(max = 5000)
        String description,

        @Schema(description = "공정명", example = "철근콘크리트공사")
        @Size(max = 100)
        String process,

        @Schema(description = "시공·촬영 일시 (오프셋 포함, 현재 시각 + 5분 이전)", example = "2026-11-16T14:20:00+09:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        OffsetDateTime capturedAt,

        @Schema(description = "시공 위치")
        @Valid
        LocationRequest location,

        @Schema(description = "외부 참조 URL (http/https)", example = "https://example.com/drawing/301")
        @Size(max = 2048)
        @Pattern(regexp = "^https?://\\S+$", message = "http 또는 https URL이어야 합니다.")
        String referenceUrl
) {
}
