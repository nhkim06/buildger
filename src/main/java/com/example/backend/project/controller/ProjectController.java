package com.example.backend.project.controller;

import com.example.backend.global.common.PageResponse;
import com.example.backend.global.error.ErrorResponse;
import com.example.backend.global.security.AuthUser;
import com.example.backend.project.dto.ProjectCreateRequest;
import com.example.backend.project.dto.ProjectResponse;
import com.example.backend.project.dto.ProjectUpdateRequest;
import com.example.backend.project.entity.ProjectStatus;
import com.example.backend.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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

@Tag(name = "Project", description = "프로젝트 CRUD")
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "프로젝트 생성", description = "시공사(2)·시행사(3) 계정만 생성할 수 있으며 생성자는 OWNER가 된다.")
    @ApiResponse(responseCode = "201", description = "생성 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "ROLE_NOT_ALLOWED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<ProjectResponse> create(@AuthenticationPrincipal AuthUser user,
                                                  @Valid @RequestBody ProjectCreateRequest request) {
        ProjectResponse response = projectService.create(user, request);
        return ResponseEntity.created(URI.create("/api/projects/" + response.projectId())).body(response);
    }

    @Operation(summary = "내 프로젝트 목록", description = "요청자가 OWNER이거나 참여자로 등록된, 삭제되지 않은 프로젝트를 조회한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public PageResponse<ProjectResponse> list(
            @AuthenticationPrincipal AuthUser user,
            @Parameter(description = "프로젝트 이름 부분 일치") @RequestParam(required = false) String keyword,
            @Parameter(description = "진행 상태") @RequestParam(required = false) ProjectStatus status,
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "페이지 크기 (1~100)") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return projectService.list(user, keyword, status, page, size);
    }

    @Operation(summary = "프로젝트 상세")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{projectId}")
    public ProjectResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId) {
        return projectService.get(projectId, user);
    }

    @Operation(summary = "프로젝트 수정", description = "OWNER·MANAGER만 가능. 전달된 필드만 수정한다.")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED / INSUFFICIENT_PROJECT_ROLE", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{projectId}")
    public ProjectResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId,
                                  @Valid @RequestBody ProjectUpdateRequest request) {
        return projectService.update(projectId, user, request);
    }

    @Operation(summary = "프로젝트 삭제 (Soft Delete)", description = "OWNER만 가능. detail.deletion만 기록하며 하위 기록·이미지 원본은 유지된다.")
    @ApiResponse(responseCode = "204", description = "삭제 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED / INSUFFICIENT_PROJECT_ROLE", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId,
                                       @Parameter(description = "삭제 사유 (최대 500자)")
                                       @RequestParam(required = false) @Size(max = 500) String reason) {
        projectService.delete(projectId, user, reason);
        return ResponseEntity.noContent().build();
    }
}
