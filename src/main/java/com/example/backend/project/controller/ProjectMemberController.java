package com.example.backend.project.controller;

import com.example.backend.global.error.ErrorResponse;
import com.example.backend.global.security.AuthUser;
import com.example.backend.project.dto.MemberAddRequest;
import com.example.backend.project.dto.MemberRoleUpdateRequest;
import com.example.backend.project.dto.ProjectMemberResponse;
import com.example.backend.project.service.ProjectMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Tag(name = "Project Member", description = "프로젝트 참여자 및 접근 권한 관리")
@RestController
@RequestMapping("/api/projects/{projectId}/members")
@RequiredArgsConstructor
@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED / INSUFFICIENT_PROJECT_ROLE", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND / MEMBER_NOT_FOUND / ACCOUNT_NOT_FOUND", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class ProjectMemberController {

    private final ProjectMemberService memberService;

    @Operation(summary = "참여자 목록", description = "OWNER가 첫 번째, 이후 참여 시각 순")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public List<ProjectMemberResponse> list(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId) {
        return memberService.list(projectId, user);
    }

    @Operation(summary = "참여자 추가", description = "OWNER·MANAGER만 가능. MANAGER 지정은 OWNER만 가능하다. loginId로 사용자를 지정한다.")
    @ApiResponse(responseCode = "201", description = "추가 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT / MEMBER_LIMIT_EXCEEDED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "ALREADY_PROJECT_MEMBER / CONCURRENT_MODIFICATION", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<ProjectMemberResponse> add(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId,
                                                     @Valid @RequestBody MemberAddRequest request) {
        ProjectMemberResponse response = memberService.add(projectId, user, request);
        return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/members/" + response.uid()))
                .body(response);
    }

    @Operation(summary = "참여자 권한 변경", description = "OWNER·MANAGER만 가능. MANAGER는 다른 MANAGER를 변경하거나 MANAGER를 지정할 수 없다.")
    @ApiResponse(responseCode = "200", description = "변경 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "CANNOT_MODIFY_OWNER / CONCURRENT_MODIFICATION", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{uid}")
    public ProjectMemberResponse changeRole(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId,
                                            @PathVariable UUID uid,
                                            @Valid @RequestBody MemberRoleUpdateRequest request) {
        return memberService.changeRole(projectId, uid, user, request);
    }

    @Operation(summary = "참여자 제외 / 탈퇴", description = "OWNER·MANAGER는 참여자를 제외할 수 있고, OWNER를 제외한 참여자는 본인 uid로 탈퇴할 수 있다.")
    @ApiResponse(responseCode = "204", description = "제외 성공")
    @ApiResponse(responseCode = "409", description = "CANNOT_MODIFY_OWNER / CONCURRENT_MODIFICATION", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId,
                                       @PathVariable UUID uid) {
        memberService.remove(projectId, uid, user);
        return ResponseEntity.noContent().build();
    }
}
