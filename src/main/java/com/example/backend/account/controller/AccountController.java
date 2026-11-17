package com.example.backend.account.controller;

import com.example.backend.account.dto.AccountResponse;
import com.example.backend.account.dto.AccountUpdateRequest;
import com.example.backend.account.dto.PasswordChangeRequest;
import com.example.backend.account.service.AccountService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account", description = "계정 정보 조회 및 수정")
@RestController
@RequestMapping("/api/accounts/me")
@RequiredArgsConstructor
@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class AccountController {

    private final AccountService accountService;

    @Operation(summary = "내 계정 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public AccountResponse getMe(@AuthenticationPrincipal AuthUser user) {
        return accountService.getMe(user.uid());
    }

    @Operation(summary = "내 계정 수정", description = "이름·소속기관·전화번호 중 전달된 필드만 수정한다. loginId·roleLevel은 변경할 수 없다.")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping
    public AccountResponse updateMe(@AuthenticationPrincipal AuthUser user,
                                    @Valid @RequestBody AccountUpdateRequest request) {
        return accountService.updateMe(user.uid(), request);
    }

    @Operation(summary = "비밀번호 변경", description = "이미 발급된 토큰은 만료(2시간) 시까지 유효하다.")
    @ApiResponse(responseCode = "204", description = "변경 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT / SAME_AS_CURRENT_PASSWORD", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "INVALID_CURRENT_PASSWORD", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthUser user,
                                               @Valid @RequestBody PasswordChangeRequest request) {
        accountService.changePassword(user.uid(), request);
        return ResponseEntity.noContent().build();
    }
}
