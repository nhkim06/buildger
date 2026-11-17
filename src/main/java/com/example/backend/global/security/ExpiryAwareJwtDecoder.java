package com.example.backend.global.security;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

/**
 * 만료된 토큰을 구분해 TOKEN_EXPIRED 응답을 할 수 있도록 감싸는 디코더.
 */
public class ExpiryAwareJwtDecoder implements JwtDecoder {

    private final JwtDecoder delegate;

    public ExpiryAwareJwtDecoder(JwtDecoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            return delegate.decode(token);
        } catch (JwtValidationException e) {
            boolean expired = e.getErrors().stream()
                    .anyMatch(error -> error.getDescription() != null
                            && error.getDescription().toLowerCase().contains("expired"));
            if (expired) {
                throw new ExpiredTokenException(e);
            }
            throw e;
        }
    }

    public static class ExpiredTokenException extends BadJwtException {
        public ExpiredTokenException(Throwable cause) {
            super("Token expired", cause);
        }
    }
}
