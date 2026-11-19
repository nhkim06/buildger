package com.example.backend.support;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * 테스트용 실제 이미지 생성. seed가 다르면 내용(해시)이 다른 이미지가 만들어진다.
 */
public final class TestImages {

    private TestImages() {
    }

    public static byte[] png(int seed) {
        return encode(seed, "png", 32, 24);
    }

    public static byte[] jpeg(int seed) {
        return encode(seed, "jpeg", 40, 30);
    }

    public static byte[] png(int seed, int width, int height) {
        return encode(seed, "png", width, height);
    }

    private static byte[] encode(int seed, String format, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, new Color((seed * 37 + x) % 256, (seed * 91 + y) % 256, (seed * 13) % 256).getRGB());
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
