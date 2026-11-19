package com.example.backend.ai.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * AI 서버 연동 설정 (docs/api.md 13.0)
 */
@ConfigurationProperties(prefix = "ai.server")
public record AiServerProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
