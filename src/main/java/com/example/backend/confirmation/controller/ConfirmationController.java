package com.example.backend.confirmation.controller;

import com.example.backend.confirmation.dto.ConfirmationRequest;
import com.example.backend.confirmation.dto.ConfirmationResponse;
import com.example.backend.confirmation.service.ConfirmationService;
import com.example.backend.global.error.ErrorResponse;
import com.example.backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Confirmation", description = "담당자 확인 및 서명 기록 관리")
@RestController
@RequestMapping("/api/projects/{projectId}/records/{dataId}/confirmations")
@RequiredArgsConstructor
@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED / ROLE_NOT_ALLOWED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND / RECORD_NOT_FOUND", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class ConfirmationController {

    private final ConfirmationService confirmationService;

    @Operation(summary = "확인·서명 등록",
            description = "시공사·시행사 계정인 프로젝트 참여자만 가능하며 본인 작성 기록은 확인할 수 없다. "
                    + "서명 시점 기록 데이터의 SHA-256을 저장하고, 이후 기록은 수정·삭제할 수 없다. 확인은 추가만 가능하다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    encoding = @Encoding(name = "request", contentType = MediaType.APPLICATION_JSON_VALUE))))
    @ApiResponse(responseCode = "201", description = "등록 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT / INVALID_IMAGE / CONFIRMATION_LIMIT_EXCEEDED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "SELF_CONFIRMATION_NOT_ALLOWED / RECORD_HAS_NO_FILES / RECORD_SUPERSEDED / RECORD_INVALIDATED / DUPLICATE_CONFIRMATION / INTEGRITY_MISMATCH / CONCURRENT_MODIFICATION", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "415", description = "UNSUPPORTED_FILE_TYPE", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ConfirmationResponse> confirm(
            @AuthenticationPrincipal AuthUser user, @PathVariable Long projectId, @PathVariable Long dataId,
            @Valid @RequestPart("request") ConfirmationRequest request,
            @RequestPart("signature") MultipartFile signature) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(confirmationService.confirm(projectId, dataId, user, request, signature));
    }

    @Operation(summary = "확인·서명 이력 조회", description = "확인 시각 오름차순")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public List<ConfirmationResponse> list(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId,
                                           @PathVariable Long dataId) {
        return confirmationService.list(projectId, dataId, user);
    }
}
