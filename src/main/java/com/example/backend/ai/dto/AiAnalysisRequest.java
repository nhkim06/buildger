package com.example.backend.ai.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "AI 분석 요청")
public record AiAnalysisRequest(
        @ArraySchema(arraySchema = @Schema(description = "분석 대상 이미지 storageId (1~5개, 미입력 시 최근 업로드 이미지 최대 5개)",
                example = "[15]"))
        @Size(min = 1, max = 5)
        List<@NotNull Long> targetStorageIds
) {
}
