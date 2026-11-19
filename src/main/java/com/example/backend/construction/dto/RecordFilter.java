package com.example.backend.construction.dto;

import com.example.backend.construction.entity.ConfirmationState;
import com.example.backend.construction.entity.RecordStatus;
import com.example.backend.construction.entity.RecordType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 프로젝트 내 시공 기록 목록 조건 (docs/api.md 9.2)
 */
public record RecordFilter(
        @Schema(description = "capturedAt 시작일 (포함, KST)", example = "2026-11-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @Schema(description = "capturedAt 종료일 (포함, KST)", example = "2026-11-30")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @Schema(description = "기록 유형") RecordType type,
        @Schema(description = "기록 상태") RecordStatus status,
        @Schema(description = "서명 여부") Boolean locked,
        @Schema(description = "공정명 부분 일치") String process,
        @Schema(description = "location.site 부분 일치") String site,
        @Schema(description = "location.building 부분 일치") String building,
        @Schema(description = "location.floor 일치 (대소문자 무시)") String floor,
        @Schema(description = "location.zone 부분 일치") String zone,
        @Schema(description = "title·description 부분 일치") String keyword,
        @Schema(description = "작성자 uid") UUID uploadedBy,
        @Schema(description = "최근 확인 상태") ConfirmationState confirmationStatus,
        @Schema(description = "정렬", example = "capturedAt,desc",
                allowableValues = {"capturedAt,desc", "capturedAt,asc", "createdAt,desc", "createdAt,asc"})
        @Pattern(regexp = "^(capturedAt|createdAt),(asc|desc)$", message = "지원하지 않는 정렬입니다.") String sort,
        @Schema(description = "페이지 번호 (0부터)", example = "0") @Min(0) Integer page,
        @Schema(description = "페이지 크기 (1~100)", example = "20") @Min(1) @Max(100) Integer size
) {
}
