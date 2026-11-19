package com.example.backend.construction.dto;

import com.example.backend.account.dto.AccountSummary;
import com.example.backend.construction.entity.ConfirmationState;
import com.example.backend.construction.entity.RecordStatus;
import com.example.backend.file.dto.FileResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "시공 기록")
public record RecordResponse(
        @Schema(description = "기록 ID", example = "101") Long dataId,
        @Schema(description = "프로젝트 ID", example = "1") Long projectId,
        @Schema(description = "기록 유형", example = "PHOTO") String type,
        @Schema(description = "제목") String title,
        @Schema(description = "내용", nullable = true) String description,
        @Schema(description = "공정명", nullable = true) String process,
        @Schema(description = "시공·촬영 일시 (UTC)", example = "2026-11-16T05:20:00Z") String capturedAt,
        @Schema(description = "위치", nullable = true) LocationResponse location,
        @Schema(description = "외부 참조 URL (data.url)", nullable = true) String referenceUrl,
        @Schema(description = "작성자", nullable = true) AccountSummary uploadedBy,
        @Schema(description = "삭제되지 않은 첨부 이미지") List<FileResponse> files,
        @Schema(description = "기록 상태 (계산값)") RecordStatus status,
        @Schema(description = "서명되어 본문 변경이 불가능한지 여부") boolean locked,
        @Schema(description = "최근 확인 상태") ConfirmationState confirmationStatus,
        @Schema(description = "확인·서명 건수") int confirmationCount,
        @Schema(description = "정정·무효화 기록이면 원본 연결 정보", nullable = true) CorrectionResponse correction,
        @Schema(description = "이 기록을 정정한 기록 ID", nullable = true) Long supersededByDataId,
        @Schema(description = "이 기록을 무효화한 기록 ID", nullable = true) Long invalidatedByDataId,
        @Schema(description = "최신 AI 분석 요약", nullable = true) AiAnalysisSummary latestAiAnalysis,
        @Schema(description = "서명 해시 검증 결과") IntegrityResponse integrity,
        @Schema(description = "등록 시각") String createdAt,
        @Schema(description = "수정 시각") String updatedAt
) {
}
