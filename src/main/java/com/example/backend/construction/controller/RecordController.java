package com.example.backend.construction.controller;

import com.example.backend.construction.dto.RecordFilter;
import com.example.backend.construction.dto.RecordRequest;
import com.example.backend.construction.dto.RecordResponse;
import com.example.backend.construction.dto.RecordUpdateRequest;
import com.example.backend.construction.service.RecordService;
import com.example.backend.global.common.PageResponse;
import com.example.backend.global.error.ErrorResponse;
import com.example.backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "Construction Record", description = "시공 기록 CRUD")
@RestController
@RequestMapping("/api/projects/{projectId}/records")
@RequiredArgsConstructor
@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED / INSUFFICIENT_PROJECT_ROLE", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND / RECORD_NOT_FOUND", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class RecordController {

    private final RecordService recordService;

    @Operation(summary = "시공 기록 생성", description = "OWNER·MANAGER·MEMBER. 이미지는 생성 후 파일 업로드 API로 첨부한다.")
    @ApiResponse(responseCode = "201", description = "생성 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<RecordResponse> create(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId,
                                                 @Valid @RequestBody RecordRequest request) {
        RecordResponse response = recordService.create(projectId, user, request);
        return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/records/" + response.dataId()))
                .body(response);
    }

    @Operation(summary = "시공 기록 목록", description = "프로젝트 내 기록을 날짜·위치·상태 조건으로 조회한다. 삭제된 기록은 제외된다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public PageResponse<RecordResponse> list(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId,
                                             @Valid @ParameterObject RecordFilter filter) {
        return recordService.list(projectId, user, filter);
    }

    @Operation(summary = "시공 기록 상세", description = "서명 해시를 현재 데이터와 비교한 결과(integrity)를 포함한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/{dataId}")
    public RecordResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId,
                              @PathVariable Long dataId,
                              @Parameter(description = "true면 이미지 바이너리 SHA-256까지 다시 검증")
                              @RequestParam(defaultValue = "false") boolean verifyFiles) {
        return recordService.get(projectId, dataId, user, verifyFiles);
    }

    @Operation(summary = "시공 기록 수정", description = "서명 전 기록만 수정할 수 있다. OWNER·MANAGER 또는 작성자 본인(MEMBER).")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "RECORD_LOCKED / CONCURRENT_MODIFICATION", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{dataId}")
    public RecordResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId,
                                 @PathVariable Long dataId, @Valid @RequestBody RecordUpdateRequest request) {
        return recordService.update(projectId, dataId, user, request);
    }

    @Operation(summary = "시공 기록 삭제 (Soft Delete)", description = "서명 전 기록만 삭제할 수 있다. 서명된 기록은 무효화 API를 사용한다.")
    @ApiResponse(responseCode = "204", description = "삭제 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "RECORD_LOCKED / RECORD_HAS_CORRECTIONS / CONCURRENT_MODIFICATION", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{dataId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId,
                                       @PathVariable Long dataId,
                                       @Parameter(description = "삭제 사유 (최대 500자)")
                                       @RequestParam(required = false) @Size(max = 500) String reason) {
        recordService.delete(projectId, dataId, user, reason);
        return ResponseEntity.noContent().build();
    }
}
