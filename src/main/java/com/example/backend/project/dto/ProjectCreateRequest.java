package com.example.backend.project.dto;

import com.example.backend.project.entity.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "프로젝트 생성 요청")
public record ProjectCreateRequest(
        @Schema(description = "프로젝트 이름", example = "OO빌딩 신축공사", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 255)
        String name,

        @Schema(description = "설명", example = "지하 3층, 지상 15층 업무시설")
        @Size(max = 2000)
        String description,

        @Schema(description = "현장 주소", example = "서울특별시 강남구 테헤란로 123")
        @Size(max = 255)
        String address,

        @Schema(description = "착공일", example = "2026-03-01", type = "string", format = "date")
        LocalDate startDate,

        @Schema(description = "준공 예정일 (착공일 이후)", example = "2027-08-31", type = "string", format = "date")
        LocalDate endDate,

        @Schema(description = "진행 상태 (기본값 PLANNED)", example = "IN_PROGRESS")
        ProjectStatus status
) {
}
