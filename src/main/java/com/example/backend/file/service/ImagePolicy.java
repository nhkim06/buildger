package com.example.backend.file.service;

import lombok.Getter;

/**
 * 이미지 용도별 제한 (docs/api.md 11.0)
 */
@Getter
public enum ImagePolicy {

    PHOTO(10L * 1024 * 1024, 10_000, 40_000_000L),
    SIGNATURE(1024L * 1024, 4_000, 16_000_000L);

    private final long maxBytes;
    private final int maxSide;
    private final long maxPixels;

    ImagePolicy(long maxBytes, int maxSide, long maxPixels) {
        this.maxBytes = maxBytes;
        this.maxSide = maxSide;
        this.maxPixels = maxPixels;
    }
}
