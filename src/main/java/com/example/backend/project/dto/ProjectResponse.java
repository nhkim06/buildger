package com.example.backend.project.dto;

import com.example.backend.account.dto.AccountSummary;
import com.example.backend.project.entity.ProjectRole;
import com.example.backend.project.entity.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프로젝트 정보")
public record ProjectResponse(
        @Schema(description = "프로젝트 ID", example = "1") Long projectId,
        @Schema(description = "프로젝트 이름 (pname)", example = "OO빌딩 신축공사") String name,
        @Schema(description = "설명", nullable = true) String description,
        @Schema(description = "현장 주소", nullable = true) String address,
        @Schema(description = "착공일", example = "2026-03-01", nullable = true) String startDate,
        @Schema(description = "준공 예정일", example = "2027-08-31", nullable = true) String endDate,
        @Schema(description = "진행 상태", example = "IN_PROGRESS") ProjectStatus status,
        @Schema(description = "프로젝트 생성자 (OWNER)", nullable = true) AccountSummary owner,
        @Schema(description = "요청자의 프로젝트 역할", example = "OWNER") ProjectRole myRole,
        @Schema(description = "OWNER 포함 참여자 수", example = "1") int memberCount,
        @Schema(description = "생성 시각", example = "2026-11-17T00:30:00Z") String createdAt,
        @Schema(description = "수정 시각", example = "2026-11-17T00:30:00Z") String updatedAt
) {
}
