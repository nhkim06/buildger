package com.example.backend.construction.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

@Schema(description = "시공 위치")
public record LocationRequest(
        @Schema(description = "구역/동", example = "A동") @Size(max = 100) String site,
        @Schema(description = "건물명", example = "본관") @Size(max = 100) String building,
        @Schema(description = "층", example = "3F") @Size(max = 20) String floor,
        @Schema(description = "세부 위치·그리드", example = "X1-X3 / Y1-Y4") @Size(max = 100) String zone,
        @Schema(description = "주소", example = "서울특별시 강남구 테헤란로 123") @Size(max = 255) String address,
        @Schema(description = "위도 (-90~90, longitude와 함께 입력)", example = "37.501274")
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @Schema(description = "경도 (-180~180, latitude와 함께 입력)", example = "127.039585")
        @DecimalMin("-180") @DecimalMax("180") Double longitude
) {

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "위도와 경도는 함께 입력해야 합니다.")
    public boolean isCoordinatePairValid() {
        return (latitude == null) == (longitude == null);
    }
}
