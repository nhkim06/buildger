package com.example.backend.account.controller;

import com.example.backend.account.dto.AccountResponse;
import com.example.backend.account.dto.LoginRequest;
import com.example.backend.account.dto.LoginResponse;
import com.example.backend.account.dto.SignupRequest;
import com.example.backend.account.service.AuthService;
import com.example.backend.global.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "Auth", description = "회원가입 및 로그인")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@SecurityRequirements
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "관리자 승인 없이 계정을 생성한다. 가입만으로는 어떤 프로젝트에도 접근할 수 없다.")
    @ApiResponse(responseCode = "201", description = "가입 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "DUPLICATE_LOGIN_ID", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/signup")
    public ResponseEntity<AccountResponse> signup(@Valid @RequestBody SignupRequest request) {
        AccountResponse response = authService.signup(request);
        return ResponseEntity.created(URI.create("/api/accounts/me")).body(response);
    }

    @Operation(summary = "로그인", description = "loginId·비밀번호를 검증하고 2시간 유효한 Access Token을 발급한다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "LOGIN_FAILED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
