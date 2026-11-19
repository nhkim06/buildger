package com.example.backend.file.service;

import com.example.backend.global.common.Hashes;
import com.example.backend.global.error.BusinessException;
import com.example.backend.global.error.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * 이미지 파일 검증 (docs/api.md 11.0 검증 순서).
 * 클라이언트가 보낸 Content-Type은 사용하지 않고, 매직 바이트로 실제 형식을 판별한 뒤
 * JDK ImageIO로 헤더만 파싱해 손상·위장 파일과 과도한 픽셀 수를 차단한다.
 */
@Component
public class ImageFileValidator {

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final Set<String> JPEG_EXTENSIONS = Set.of("jpg", "jpeg");
    private static final Set<String> PNG_EXTENSIONS = Set.of("png");

    static {
        ImageIO.setUseCache(false);
    }

    public ValidatedImage validate(MultipartFile file, ImagePolicy policy) {
        // 1. 빈 파일
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "빈 파일은 업로드할 수 없습니다.");
        }
        // 2. 크기
        if (file.getSize() > policy.getMaxBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일을 읽을 수 없습니다.");
        }
        // 3. 매직 바이트
        ImageFormat format = detect(bytes);
        if (format == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        // 4. 확장자 일치
        String originalName = sanitizeFileName(file.getOriginalFilename(), format);
        String extension = extensionOf(originalName);
        if (extension == null || !format.extensions.contains(extension)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE, "파일 확장자와 실제 이미지 형식이 일치하지 않습니다.");
        }
        // 5~6. 헤더 파싱, 픽셀 제한
        int[] size = readDimensions(bytes, format);
        int width = size[0];
        int height = size[1];
        if (width < 1 || height < 1 || width > policy.getMaxSide() || height > policy.getMaxSide()
                || (long) width * height > policy.getMaxPixels()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "이미지 해상도가 허용 범위를 벗어났습니다.");
        }
        // 7. SHA-256
        return new ValidatedImage(originalName, format.mimeType, bytes, width, height, Hashes.sha256Hex(bytes));
    }

    private static ImageFormat detect(byte[] bytes) {
        if (startsWith(bytes, PNG_MAGIC)) {
            return ImageFormat.PNG;
        }
        if (startsWith(bytes, JPEG_MAGIC)) {
            return ImageFormat.JPEG;
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int[] readDimensions(byte[] bytes, ImageFormat format) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format.readerName);
        if (!readers.hasNext()) {
            throw new IllegalStateException("No ImageIO reader for " + format.readerName);
        }
        ImageReader reader = readers.next();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(input, true, true);
            return new int[]{reader.getWidth(0), reader.getHeight(0)};
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        } finally {
            reader.dispose();
        }
    }

    /** 경로 문자·제어 문자를 제거하고 255자로 자른다. (docs/api.md 11.0) */
    static String sanitizeFileName(String name, ImageFormat format) {
        String value = name == null ? "" : name;
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        value = value.substring(slash + 1).replace("..", "").replaceAll("\\p{Cntrl}", "").strip();
        if (value.isEmpty()) {
            value = "image." + format.extensions.iterator().next();
        }
        if (value.length() > 255) {
            String ext = extensionOf(value);
            value = ext == null ? value.substring(0, 255)
                    : value.substring(0, 255 - ext.length() - 1) + "." + ext;
        }
        return value;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    enum ImageFormat {
        JPEG("image/jpeg", "jpeg", JPEG_EXTENSIONS),
        PNG("image/png", "png", PNG_EXTENSIONS);

        private final String mimeType;
        private final String readerName;
        private final Set<String> extensions;

        ImageFormat(String mimeType, String readerName, Set<String> extensions) {
            this.mimeType = mimeType;
            this.readerName = readerName;
            this.extensions = extensions;
        }
    }
}
