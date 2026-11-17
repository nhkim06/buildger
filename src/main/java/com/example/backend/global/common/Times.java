package com.example.backend.global.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 시각 처리 규칙 (docs/api.md 1.1, 3.0).
 * 저장·응답 시각은 초 단위로 자른 UTC {@code yyyy-MM-dd'T'HH:mm:ss'Z'} 문자열을 사용한다.
 */
public final class Times {

    public static final ZoneId SEARCH_ZONE = ZoneId.of("Asia/Seoul");

    private Times() {
    }

    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    public static String nowString() {
        return format(now());
    }

    public static String format(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    public static String format(OffsetDateTime dateTime) {
        return dateTime == null ? null : format(dateTime.toInstant());
    }

    /** 검색 시작일(KST 00:00:00)을 UTC 저장 형식으로 변환한다. */
    public static String startOfDay(LocalDate date) {
        return format(date.atStartOfDay(SEARCH_ZONE).toInstant());
    }

    /** 검색 종료일(KST 23:59:59)을 UTC 저장 형식으로 변환한다. */
    public static String endOfDay(LocalDate date) {
        return format(date.plusDays(1).atStartOfDay(SEARCH_ZONE).toInstant().minusSeconds(1));
    }
}
