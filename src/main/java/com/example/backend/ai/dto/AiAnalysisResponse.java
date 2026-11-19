package com.example.backend.ai.dto;

import com.example.backend.construction.entity.AiAnalysis;
import com.example.backend.construction.entity.AiAnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "AI 분석 결과")
public record AiAnalysisResponse(
        @Schema(description = "분석 ID") String analysisId,
        @Schema(description = "기록 내 분석 순번 (1부터)", example = "1") Integer version,
        @Schema(description = "SUCCESS / FAILED") AiAnalysisStatus status,
        @Schema(description = "AI 서버가 반환한 모델명·버전", nullable = true) String model,
        @Schema(description = "분석 대상 이미지 storageId") List<Long> targetStorageIds,
        @Schema(description = "요약", nullable = true) String summary,
        @Schema(description = "분석 결과 JSON", nullable = true) Object result,
        @Schema(description = "AI_SERVER_ERROR / AI_SERVER_TIMEOUT / AI_INVALID_RESPONSE", nullable = true) String errorCode,
        @Schema(description = "실패 사유", nullable = true) String errorMessage,
        @Schema(description = "요청자") Requester requestedBy,
        @Schema(description = "요청 시각") String requestedAt,
        @Schema(description = "결과 저장 시각") String completedAt
) {

    public static AiAnalysisResponse of(AiAnalysis a, String requesterName) {
        UUID uid = a.getRequestedBy() == null ? null : UUID.fromString(a.getRequestedBy());
        return new AiAnalysisResponse(a.getAnalysisId(), a.getVersion(), a.getStatus(), a.getModel(),
                a.getTargetStorageIds(), a.getSummary(), a.getResult(), a.getErrorCode(), a.getErrorMessage(),
                new Requester(uid, requesterName), a.getRequestedAt(), a.getCompletedAt());
    }

    @Schema(description = "요청자")
    public record Requester(UUID uid, String name) {
    }
}
