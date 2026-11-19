package com.example.backend.construction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "무효화 기록 생성 요청")
public record InvalidationRequest(
        @Schema(description = "무효화 사유", example = "다른 현장 사진이 잘못 등록됨", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 1000)
        String reason
) {
}
