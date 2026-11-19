package com.example.backend.file.dto;

import com.example.backend.construction.entity.FileEntry;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "이미지 파일 정보")
public record FileResponse(
        @Schema(description = "파일 ID (storage_id)", example = "15") Long storageId,
        @Schema(description = "원본 파일명", example = "slab_rebar_01.jpg") String originalName,
        @Schema(description = "서버가 판별한 MIME 타입", example = "image/jpeg") String contentType,
        @Schema(description = "바이트 크기", example = "2483021") Long size,
        @Schema(description = "가로 픽셀", example = "4032") Integer width,
        @Schema(description = "세로 픽셀", example = "3024") Integer height,
        @Schema(description = "SHA-256 (64자리 hex)") String sha256,
        @Schema(description = "다운로드 URL", example = "/api/files/15") String downloadUrl,
        @Schema(description = "업로드 사용자 uid") String uploadedBy,
        @Schema(description = "업로드 시각", example = "2026-11-17T00:41:00Z") String uploadedAt
) {

    public static FileResponse from(FileEntry entry) {
        return new FileResponse(entry.getStorageId(), entry.getOriginalName(), entry.getContentType(),
                entry.getSize(), entry.getWidth(), entry.getHeight(), entry.getSha256(),
                downloadUrl(entry.getStorageId()), entry.getUploadedBy(), entry.getUploadedAt());
    }

    public static String downloadUrl(Long storageId) {
        return "/api/files/" + storageId;
    }
}
