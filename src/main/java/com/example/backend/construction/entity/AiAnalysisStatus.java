package com.example.backend.construction.entity;

/**
 * AI 분석 상태. PENDING은 향후 비동기 처리용 예약값이다. (docs/api.md 13.1)
 */
public enum AiAnalysisStatus {
    SUCCESS,
    FAILED,
    PENDING
}
