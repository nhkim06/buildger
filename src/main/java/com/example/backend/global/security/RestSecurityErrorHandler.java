package com.example.backend.global.security;

import com.example.backend.global.error.ErrorCode;
import com.example.backend.global.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security 단계의 401·403을 공통 오류 형식으로 응답한다.
 */
@Component
@RequiredArgsConstructor
public class RestSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode code = isExpired(authException) ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHORIZED;
        write(response, request, code);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, request, ErrorCode.ACCESS_DENIED);
    }

    private boolean isExpired(Throwable e) {
        for (Throwable cur = e; cur != null; cur = cur.getCause()) {
            if (cur instanceof ExpiryAwareJwtDecoder.ExpiredTokenException) {
                return true;
            }
        }
        return false;
    }

    private void write(HttpServletResponse response, HttpServletRequest request, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorResponse body = ErrorResponse.of(code, code.getMessage(), request.getRequestURI(), null);
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }
}
