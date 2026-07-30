package com.grun.calorietracker.service;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.service.support.FoodProductEvidenceImageInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoodProductEvidenceImageInspectorTest {
    private FoodContributionStorageProperties properties;
    private FoodProductEvidenceImageInspector inspector;

    @BeforeEach
    void setUp() {
        properties = new FoodContributionStorageProperties();
        inspector = new FoodProductEvidenceImageInspector(properties);
    }

    @Test
    void validatesPngSignatureChecksumAndDimensions() {
        byte[] png = png(1200, 800);
        var dimensions = inspector.inspect(png, "image/png", sha256(png));
        assertEquals(1200, dimensions.width());
        assertEquals(800, dimensions.height());
    }

    @Test
    void rejectsChecksumMismatch() {
        assertThrows(IllegalArgumentException.class,
                () -> inspector.inspect(png(100, 100), "image/png", "0".repeat(64)));
    }

    @Test
    void rejectsDeclaredMimeWithWrongSignature() {
        byte[] png = png(100, 100);
        assertThrows(IllegalArgumentException.class,
                () -> inspector.inspect(png, "image/jpeg", sha256(png)));
    }

    @Test
    void rejectsDecodedPixelBomb() {
        properties.setMaxDecodedPixels(1_000_000);
        byte[] png = png(2000, 2000);
        assertThrows(IllegalArgumentException.class,
                () -> inspector.inspect(png, "image/png", sha256(png)));
    }

    @Test
    void decodesWebpExtendedDimensionsWithoutExternalCodec() {
        byte[] webp = new byte[30];
        putAscii(webp, 0, "RIFF");
        putAscii(webp, 8, "WEBP");
        putAscii(webp, 12, "VP8X");
        int widthMinusOne = 639;
        int heightMinusOne = 479;
        putLe24(webp, 24, widthMinusOne);
        putLe24(webp, 27, heightMinusOne);
        var dimensions = inspector.inspect(webp, "image/webp", sha256(webp));
        assertEquals(640, dimensions.width());
        assertEquals(480, dimensions.height());
    }

    private byte[] png(int width, int height) {
        byte[] bytes = new byte[24];
        int[] signature = {0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        for (int i = 0; i < signature.length; i++) bytes[i] = (byte) signature[i];
        putAscii(bytes, 12, "IHDR");
        putBe32(bytes, 16, width);
        putBe32(bytes, 20, height);
        return bytes;
    }

    private void putAscii(byte[] bytes, int offset, String value) {
        for (int i = 0; i < value.length(); i++) bytes[offset + i] = (byte) value.charAt(i);
    }

    private void putBe32(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    private void putLe24(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
