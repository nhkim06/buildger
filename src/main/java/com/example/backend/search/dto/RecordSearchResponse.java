package com.example.backend.search.dto;

import com.example.backend.account.dto.AccountSummary;
import com.example.backend.construction.dto.AiAnalysisSummary;
import com.example.backend.construction.dto.CorrectionResponse;
import com.example.backend.construction.dto.IntegrityResponse;
import com.example.backend.construction.dto.LocationResponse;
import com.example.backend.construction.dto.RecordResponse;
import com.example.backend.construction.entity.ConfirmationState;
import com.example.backend.construction.entity.RecordStatus;
import com.example.backend.file.dto.FileResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "시공 이력 검색 결과 (RecordResponse에서 files 대신 thumbnail·fileCount, projectName·distanceMeters 추가)")
public record RecordSearchResponse(
        Long dataId,
        Long projectId,
        @Schema(description = "프로젝트 이름") String projectName,
        String type,
        String title,
        String description,
        String process,
        String capturedAt,
        LocationResponse location,
        @Schema(description = "반경 검색 시 중심으로부터의 거리(m), 그 외 null", nullable = true) Double distanceMeters,
        String referenceUrl,
        AccountSummary uploadedBy,
        @Schema(description = "첫 번째 이미지", nullable = true) Thumbnail thumbnail,
        @Schema(description = "삭제되지 않은 이미지 수") int fileCount,
        RecordStatus status,
        boolean locked,
        ConfirmationState confirmationStatus,
        int confirmationCount,
        CorrectionResponse correction,
        Long supersededByDataId,
        Long invalidatedByDataId,
        AiAnalysisSummary latestAiAnalysis,
        IntegrityResponse integrity,
        String createdAt,
        String updatedAt
) {

    public static RecordSearchResponse of(RecordResponse r, String projectName, Double distanceMeters) {
        FileResponse first = r.files().isEmpty() ? null : r.files().get(0);
        return new RecordSearchResponse(r.dataId(), r.projectId(), projectName, r.type(), r.title(), r.description(),
                r.process(), r.capturedAt(), r.location(), distanceMeters, r.referenceUrl(), r.uploadedBy(),
                first == null ? null : new Thumbnail(first.storageId(), first.downloadUrl()),
                r.files().size(), r.status(), r.locked(), r.confirmationStatus(), r.confirmationCount(),
                r.correction(), r.supersededByDataId(), r.invalidatedByDataId(), r.latestAiAnalysis(),
                r.integrity(), r.createdAt(), r.updatedAt());
    }

    @Schema(description = "썸네일 이미지")
    public record Thumbnail(Long storageId, String downloadUrl) {
    }
}
