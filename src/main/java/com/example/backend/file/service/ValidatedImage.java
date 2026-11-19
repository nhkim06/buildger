package com.example.backend.file.service;

/**
 * 검증을 통과한 이미지. contentType은 매직 바이트로 판별한 값이다.
 */
public record ValidatedImage(String originalName, String contentType, byte[] bytes,
                             int width, int height, String sha256) {

    public long size() {
        return bytes.length;
    }
}
