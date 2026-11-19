package com.example.backend.file.controller;

import com.example.backend.file.dto.FileResponse;
import com.example.backend.file.service.FileService;
import com.example.backend.file.service.FileService.FileDownload;
import com.example.backend.global.error.ErrorResponse;
import com.example.backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Tag(name = "File", description = "이미지 업로드·조회 (JPEG·PNG만 허용)")
@RestController
@RequiredArgsConstructor
@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED / INSUFFICIENT_PROJECT_ROLE", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class FileController {

    private final FileService fileService;

    @Operation(summary = "이미지 업로드",
            description = "JPEG·PNG 1~10장 (장당 10MB). 매직 바이트·확장자·헤더·해상도를 검증하고 SHA-256을 저장한다. 서명된 기록에는 업로드할 수 없다.")
    @ApiResponse(responseCode = "201", description = "업로드 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT / INVALID_IMAGE / FILE_COUNT_EXCEEDED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND / RECORD_NOT_FOUND", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "RECORD_LOCKED / INVALID_RECORD_TYPE / DUPLICATE_FILE / CONCURRENT_MODIFICATION", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "415", description = "UNSUPPORTED_FILE_TYPE", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(value = "/api/projects/{projectId}/records/{dataId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<FileResponse>> upload(
            @AuthenticationPrincipal AuthUser user, @PathVariable Long projectId, @PathVariable Long dataId,
            @Parameter(description = "JPEG·PNG 이미지 1~10장",
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            array = @ArraySchema(schema = @Schema(type = "string", format = "binary"))))
            @RequestPart("files") List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fileService.upload(projectId, dataId, user, files));
    }

    @Operation(summary = "이미지 목록", description = "삭제되지 않은 이미지 (업로드 시각 순)")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND / RECORD_NOT_FOUND", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/projects/{projectId}/records/{dataId}/files")
    public List<FileResponse> list(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId,
                                   @PathVariable Long dataId) {
        return fileService.list(projectId, dataId, user);
    }

    @Operation(summary = "이미지 다운로드", description = "파일이 속한 프로젝트의 참여자만 조회할 수 있다. 서명 이미지도 이 API로 조회한다.")
    @ApiResponse(responseCode = "200", description = "이미지 바이너리",
            content = {@Content(mediaType = MediaType.IMAGE_JPEG_VALUE, schema = @Schema(type = "string", format = "binary")),
                    @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary"))})
    @ApiResponse(responseCode = "304", description = "If-None-Match가 ETag와 같음")
    @ApiResponse(responseCode = "404", description = "FILE_NOT_FOUND", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/files/{storageId}")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal AuthUser user, @PathVariable Long storageId,
                                           @Parameter(description = "true면 attachment로 다운로드")
                                           @RequestParam(defaultValue = "false") boolean download,
                                           @Parameter(hidden = true)
                                           @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        FileDownload file = fileService.download(storageId, user);
        String etag = "\"" + file.sha256() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(file.fileName() == null ? "image" : file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.bytes());
    }

    @Operation(summary = "이미지 삭제 (Soft Delete)", description = "서명 전 기록만 가능. storage 원본은 유지된다.")
    @ApiResponse(responseCode = "204", description = "삭제 성공")
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND / RECORD_NOT_FOUND / FILE_NOT_FOUND", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "RECORD_LOCKED / CONCURRENT_MODIFICATION", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/api/projects/{projectId}/records/{dataId}/files/{storageId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long projectId,
                                       @PathVariable Long dataId, @PathVariable Long storageId) {
        fileService.delete(projectId, dataId, storageId, user);
        return ResponseEntity.noContent().build();
    }
}
