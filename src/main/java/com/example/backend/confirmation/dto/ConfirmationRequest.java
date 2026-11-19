package com.example.backend.confirmation.dto;

import com.example.backend.construction.entity.ConfirmationState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "확인·서명 요청 (multipart의 request 파트, application/json)")
public record ConfirmationRequest(
        @Schema(description = "확인 결과", example = "APPROVED", allowableValues = {"APPROVED", "REJECTED"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        ConfirmationState status,

        @Schema(description = "의견 (REJECTED이면 필수)", example = "배근 간격 확인 완료")
        @Size(max = 1000)
        String comment
) {
}
