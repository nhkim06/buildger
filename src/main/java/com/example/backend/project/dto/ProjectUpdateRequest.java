package com.example.backend.project.dto;

import com.example.backend.project.entity.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "프로젝트 수정 요청 (전달된 필드만 수정)")
public record ProjectUpdateRequest(
        @Schema(description = "프로젝트 이름", example = "OO빌딩 신축공사")
        @Size(min = 1, max = 255)
        @Pattern(regexp = "(?s).*\\S.*", message = "이름은 공백일 수 없습니다.")
        String name,

        @Schema(description = "설명")
        @Size(max = 2000)
        String description,

        @Schema(description = "현장 주소")
        @Size(max = 255)
        String address,

        @Schema(description = "착공일", example = "2026-03-01", type = "string", format = "date")
        LocalDate startDate,

        @Schema(description = "준공 예정일", example = "2027-09-15", type = "string", format = "date")
        LocalDate endDate,

        @Schema(description = "진행 상태", example = "COMPLETED")
        ProjectStatus status
) {

    public boolean hasNoChanges() {
        return name == null && description == null && address == null
                && startDate == null && endDate == null && status == null;
    }
}
