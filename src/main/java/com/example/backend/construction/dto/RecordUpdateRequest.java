package com.example.backend.construction.dto;

import com.example.backend.construction.entity.RecordType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

@Schema(description = "시공 기록 수정 요청 (서명 전 기록만, 전달된 필드만 수정, location은 전체 교체)")
public record RecordUpdateRequest(
        @Schema(description = "기록 유형 (INVALIDATION 불가)", example = "PHOTO", allowableValues = {"PHOTO", "INSPECTION", "NOTE"})
        RecordType type,

        @Schema(description = "제목", example = "3층 슬래브 철근 배근")
        @Size(min = 1, max = 200)
        @Pattern(regexp = "(?s).*\\S.*", message = "제목은 공백일 수 없습니다.")
        String title,

        @Schema(description = "내용", example = "D13 @200 배근 완료, 피복두께 30mm 확인")
        @Size(max = 5000)
        String description,

        @Schema(description = "공정명")
        @Size(max = 100)
        String process,

        @Schema(description = "시공·촬영 일시", example = "2026-11-16T14:20:00+09:00")
        OffsetDateTime capturedAt,

        @Schema(description = "시공 위치 (전달 시 객체 전체 교체)")
        @Valid
        LocationRequest location,

        @Schema(description = "외부 참조 URL")
        @Size(max = 2048)
        @Pattern(regexp = "^https?://\\S+$", message = "http 또는 https URL이어야 합니다.")
        String referenceUrl
) {

    public boolean hasNoChanges() {
        return type == null && title == null && description == null && process == null
                && capturedAt == null && location == null && referenceUrl == null;
    }
}
