package com.example.backend.search.dto;

import com.example.backend.construction.entity.ConfirmationState;
import com.example.backend.construction.entity.RecordStatus;
import com.example.backend.construction.entity.RecordType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 시공 이력 통합 검색 조건 (docs/api.md 14.1)
 */
public record RecordSearchRequest(
        @Schema(description = "특정 프로젝트로 한정 (참여 중이어야 함)", example = "1") Long projectId,
        @Schema(description = "capturedAt 시작일 (포함, KST)", example = "2026-11-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @Schema(description = "capturedAt 종료일 (포함, KST, 최대 366일)", example = "2026-11-30")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @Schema(description = "location.address 부분 일치") String address,
        @Schema(description = "location.site 부분 일치") String site,
        @Schema(description = "location.building 부분 일치") String building,
        @Schema(description = "location.floor 일치 (대소문자 무시)") String floor,
        @Schema(description = "location.zone 부분 일치") String zone,
        @Schema(description = "반경 검색 중심 위도 (longitude, radiusMeters와 함께)", example = "37.501274")
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @Schema(description = "반경 검색 중심 경도", example = "127.039585")
        @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Schema(description = "반경 (1~5000m)", example = "500") @Min(1) @Max(5000) Integer radiusMeters,
        @Schema(description = "기록 유형") RecordType type,
        @Schema(description = "기록 상태") RecordStatus status,
        @Schema(description = "title·description·process 부분 일치") String keyword,
        @Schema(description = "SUCCESS 분석 결과 존재 여부") Boolean hasAiAnalysis,
        @Schema(description = "최근 확인 상태") ConfirmationState confirmationStatus,
        @Schema(description = "정렬 (distance,asc는 반경 검색 시에만)", example = "capturedAt,desc",
                allowableValues = {"capturedAt,desc", "capturedAt,asc", "createdAt,desc", "createdAt,asc", "distance,asc"})
        @Pattern(regexp = "^((capturedAt|createdAt),(asc|desc)|distance,asc)$", message = "지원하지 않는 정렬입니다.")
        String sort,
        @Schema(description = "페이지 번호 (0부터)", example = "0") @Min(0) Integer page,
        @Schema(description = "페이지 크기 (1~100)", example = "20") @Min(1) @Max(100) Integer size
) {
}
