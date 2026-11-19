package com.example.backend.ai.controller;

import com.example.backend.ai.dto.AiAnalysisRequest;
import com.example.backend.ai.dto.AiAnalysisResponse;
import com.example.backend.ai.service.AiAnalysisService;
import com.example.backend.global.error.ErrorResponse;
import com.example.backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@Tag(name = "AI Analysis", description = "AI 분석 요청·결과 저장 및 조회")
@RestController
@RequestMapping("/api/projects/{projectId}/records/{dataId}/ai-analyses")
@RequiredArgsConstructor
@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED / INSUFFICIENT_PROJECT_ROLE", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND / RECORD_NOT_FOUND / AI_ANALYSIS_NOT_FOUND", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    @Operation(summary = "AI 분석 요청·결과 저장",
            description = "서버가 AI 서버를 동기 호출하고 결과를 data.metadata.aiAnalyses에 이력으로 저장한다. "
                    + "AI 서버 실패도 FAILED 이력으로 저장한 뒤 502·504를 응답한다. 서명된 기록에도 분석을 추가할 수 있다. "
                    + "요청 본문을 생략하면 최근 업로드 이미지 최대 5개를 분석한다.")
    @ApiResponse(responseCode = "201", description = "분석 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "NO_ANALYZABLE_FILES / INVALID_RECORD_TYPE / RECORD_INVALIDATED / AI_ANALYSIS_LIMIT_EXCEEDED / CONCURRENT_MODIFICATION", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "502", description = "AI_SERVER_ERROR (FAILED 이력 저장됨)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "504", description = "AI_SERVER_TIMEOUT (FAILED 이력 저장됨)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<AiAnalysisResponse> analyze(@AuthenticationPrincipal AuthUser user,
                                                      @PathVariable Long projectId, @PathVariable Long dataId,
                                                      @Valid @RequestBody(required = false) AiAnalysisRequest request) {
        AiAnalysisResponse response = aiAnalysisService.analyze(projectId, dataId, user, request);
        return ResponseEntity.created(URI.create(
                        "/api/projects/" + projectId + "/records/" + dataId + "/ai-analyses/latest"))
                .body(response);
    }

    @Operation(summary = "AI 분석 이력 조회", description = "version 내림차순")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public List<AiAnalysisResponse> list(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId,
                                         @PathVariable Long dataId) {
        return aiAnalysisService.list(projectId, dataId, user);
    }

    @Operation(summary = "최신 AI 분석 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/latest")
    public AiAnalysisResponse latest(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId,
                                     @PathVariable Long dataId,
                                     @Parameter(description = "true(기본)면 최신 SUCCESS, false면 상태 무관 최신")
                                     @RequestParam(defaultValue = "true") boolean successOnly) {
        return aiAnalysisService.latest(projectId, dataId, user, successOnly);
    }
}
