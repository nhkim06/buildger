package com.example.backend.global.security;

import java.util.UUID;

/**
 * 인증된 요청자. 토큰 검증 시 DB에서 읽은 현재 계정 정보로 만든다.
 */
public record AuthUser(UUID uid, short roleLevel, String name) {
}
