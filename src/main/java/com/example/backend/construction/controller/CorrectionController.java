package com.example.backend.construction.controller;

import com.example.backend.construction.dto.CorrectionRequest;
import com.example.backend.construction.dto.HistoryResponse;
import com.example.backend.construction.dto.InvalidationRequest;
import com.example.backend.construction.dto.RecordResponse;
import com.example.backend.construction.service.CorrectionService;
import com.example.backend.global.error.ErrorResponse;
import com.example.backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "Correction", description = "서명된 기록의 정정·무효화 (원본은 변경하지 않음)")
@RestController
@RequestMapping("/api/projects/{projectId}/records/{dataId}")
@RequiredArgsConstructor
@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED / INSUFFICIENT_PROJECT_ROLE", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND / RECORD_NOT_FOUND", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class CorrectionController {

    private final CorrectionService correctionService;

    @Operation(summary = "정정 기록 생성",
            description = "서명된 최신 기록을 정정하는 새 기록(AMENDMENT)을 만든다. OWNER·MANAGER 또는 원본 작성자(MEMBER). 이미지는 새 기록에 다시 업로드한다.")
    @ApiResponse(responseCode = "201", description = "생성 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "RECORD_NOT_LOCKED / RECORD_ALREADY_CORRECTED / RECORD_INVALIDATED / INVALID_RECORD_TYPE / CONCURRENT_MODIFICATION", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/corrections")
    public ResponseEntity<RecordResponse> amend(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId,
                                                @PathVariable Long dataId,
                                                @Valid @RequestBody CorrectionRequest request) {
        RecordResponse response = correctionService.amend(projectId, dataId, user, request);
        return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/records/" + response.dataId()))
                .body(response);
    }

    @Operation(summary = "무효화 기록 생성",
            description = "서명된 기록을 삭제하는 대신 무효화 기록(type INVALIDATION)을 만든다. OWNER·MANAGER만 가능.")
    @ApiResponse(responseCode = "201", description = "생성 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "RECORD_NOT_LOCKED / RECORD_ALREADY_CORRECTED / RECORD_INVALIDATED / INVALID_RECORD_TYPE / CONCURRENT_MODIFICATION", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/invalidation")
    public ResponseEntity<RecordResponse> invalidate(@AuthenticationPrincipal AuthUser user,
                                                     @PathVariable Long projectId, @PathVariable Long dataId,
                                                     @Valid @RequestBody InvalidationRequest request) {
        RecordResponse response = correctionService.invalidate(projectId, dataId, user, request);
        return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/records/" + response.dataId()))
                .body(response);
    }

    @Operation(summary = "정정 이력 조회", description = "기록이 속한 정정 체인 전체(원본 → 정정 → 무효화)를 반환한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/history")
    public HistoryResponse history(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId,
                                   @PathVariable Long dataId) {
        return correctionService.history(projectId, dataId, user);
    }
}
