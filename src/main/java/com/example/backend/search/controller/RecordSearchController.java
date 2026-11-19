package com.example.backend.search.controller;

import com.example.backend.global.common.PageResponse;
import com.example.backend.global.error.ErrorResponse;
import com.example.backend.global.security.AuthUser;
import com.example.backend.search.dto.RecordSearchRequest;
import com.example.backend.search.dto.RecordSearchResponse;
import com.example.backend.search.service.RecordSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Search", description = "날짜·위치별 시공 이력 검색")
@RestController
@RequiredArgsConstructor
public class RecordSearchController {

    private final RecordSearchService searchService;

    @Operation(summary = "시공 이력 통합 검색",
            description = "요청자가 참여 중인 삭제되지 않은 모든 프로젝트(또는 지정 프로젝트)의 시공 기록을 날짜·위치·반경 조건으로 검색한다. "
                    + "검색 기간은 최대 366일이다.")
    @ApiResponse(responseCode = "200", description = "검색 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED (projectId 지정 시)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND (projectId 지정 시)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/records/search")
    public PageResponse<RecordSearchResponse> search(@AuthenticationPrincipal AuthUser user,
                                                     @Valid @ParameterObject RecordSearchRequest request) {
        return searchService.search(user, request);
    }
}
