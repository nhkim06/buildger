package com.example.backend.construction.dto;

import com.example.backend.construction.entity.AiAnalysis;
import com.example.backend.construction.entity.AiAnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "최신 AI 분석 요약")
public record AiAnalysisSummary(
        String analysisId,
        Integer version,
        AiAnalysisStatus status,
        String summary,
        String completedAt
) {

    public static AiAnalysisSummary from(AiAnalysis analysis) {
        return new AiAnalysisSummary(analysis.getAnalysisId(), analysis.getVersion(), analysis.getStatus(),
                analysis.getSummary(), analysis.getCompletedAt());
    }
}
