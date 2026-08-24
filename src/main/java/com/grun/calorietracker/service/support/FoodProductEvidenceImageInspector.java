package com.grun.calorietracker.service.support;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class FoodProductEvidenceImageInspector {
    private final FoodContributionStorageProperties properties;

    public Dimensions inspect(byte[] bytes, String contentType, String expectedSha256) {
        if (bytes == null || bytes.length == 0 || bytes.length > properties.getMaxUploadBytes()) {
            throw new IllegalArgumentException("Product evidence size is invalid.");
        }
        String actualChecksum = sha256(bytes);
        if (expectedSha256 == null || !MessageDigest.isEqual(
                actualChecksum.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                expectedSha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Product evidence checksum does not match the reserved upload.");
        }
        Dimensions dimensions = switch (contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT)) {
            case "image/png" -> png(bytes);
            case "image/jpeg" -> jpeg(bytes);
            case "image/webp" -> webp(bytes);
            default -> throw new IllegalArgumentException("Product evidence content type is not allowed.");
        };
        long pixels = (long) dimensions.width() * dimensions.height();
        if (dimensions.width() <= 0 || dimensions.height() <= 0
                || pixels <= 0 || pixels > properties.getMaxDecodedPixels()) {
            throw new IllegalArgumentException("Product evidence decoded dimensions exceed the configured limit.");
        }
        return dimensions;
    }

    private Dimensions png(byte[] bytes) {
        int[] signature = {0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        require(matches(bytes, 0, signature) && bytes.length >= 24, "Product evidence PNG signature is invalid.");
        require(ascii(bytes, 12, "IHDR"), "Product evidence PNG header is invalid.");
        return new Dimensions(bigEndianInt(bytes, 16), bigEndianInt(bytes, 20));
    }

    private Dimensions jpeg(byte[] bytes) {
        require(bytes.length >= 4 && unsigned(bytes[0]) == 0xff && unsigned(bytes[1]) == 0xd8,
                "Product evidence JPEG signature is invalid.");
        int index = 2;
        while (index + 3 < bytes.length) {
            while (index < bytes.length && unsigned(bytes[index]) != 0xff) index++;
            while (index < bytes.length && unsigned(bytes[index]) == 0xff) index++;
            if (index >= bytes.length) break;
            int marker = unsigned(bytes[index++]);
            if (marker == 0xd8 || marker == 0xd9 || (marker >= 0xd0 && marker <= 0xd7)) continue;
            require(index + 1 < bytes.length, "Product evidence JPEG segment is truncated.");
            int length = (unsigned(bytes[index]) << 8) | unsigned(bytes[index + 1]);
            require(length >= 2 && index + length <= bytes.length, "Product evidence JPEG segment is invalid.");
            if (isStartOfFrame(marker)) {
                require(length >= 7, "Product evidence JPEG dimensions are missing.");
                int height = (unsigned(bytes[index + 3]) << 8) | unsigned(bytes[index + 4]);
                int width = (unsigned(bytes[index + 5]) << 8) | unsigned(bytes[index + 6]);
                return new Dimensions(width, height);
            }
            index += length;
        }
        throw new IllegalArgumentException("Product evidence JPEG dimensions could not be decoded.");
    }

    private Dimensions webp(byte[] bytes) {
        require(bytes.length >= 30 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP"),
                "Product evidence WebP signature is invalid.");
        if (ascii(bytes, 12, "VP8X")) {
            return new Dimensions(1 + littleEndian24(bytes, 24), 1 + littleEndian24(bytes, 27));
        }
        if (ascii(bytes, 12, "VP8L")) {
            require(unsigned(bytes[20]) == 0x2f, "Product evidence WebP lossless header is invalid.");
            int b1 = unsigned(bytes[21]), b2 = unsigned(bytes[22]);
            int b3 = unsigned(bytes[23]), b4 = unsigned(bytes[24]);
            return new Dimensions(1 + b1 + ((b2 & 0x3f) << 8),
                    1 + ((b2 & 0xc0) >> 6) + (b3 << 2) + ((b4 & 0x0f) << 10));
        }
        if (ascii(bytes, 12, "VP8 ")) {
            require(unsigned(bytes[23]) == 0x9d && unsigned(bytes[24]) == 0x01 && unsigned(bytes[25]) == 0x2a,
                    "Product evidence WebP frame header is invalid.");
            int width = (unsigned(bytes[26]) | (unsigned(bytes[27]) << 8)) & 0x3fff;
            int height = (unsigned(bytes[28]) | (unsigned(bytes[29]) << 8)) & 0x3fff;
            return new Dimensions(width, height);
        }
        throw new IllegalArgumentException("Product evidence WebP dimensions could not be decoded.");
    }

    private boolean isStartOfFrame(int marker) {
        return switch (marker) {
            case 0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf -> true;
            default -> false;
        };
    }

    private int bigEndianInt(byte[] value, int offset) {
        return (unsigned(value[offset]) << 24) | (unsigned(value[offset + 1]) << 16)
                | (unsigned(value[offset + 2]) << 8) | unsigned(value[offset + 3]);
    }

    private int littleEndian24(byte[] value, int offset) {
        return unsigned(value[offset]) | (unsigned(value[offset + 1]) << 8) | (unsigned(value[offset + 2]) << 16);
    }

    private boolean ascii(byte[] value, int offset, String expected) {
        if (offset < 0 || offset + expected.length() > value.length) return false;
        for (int i = 0; i < expected.length(); i++) {
            if (unsigned(value[offset + i]) != expected.charAt(i)) return false;
        }
        return true;
    }

    private boolean matches(byte[] value, int offset, int[] expected) {
        if (offset < 0 || offset + expected.length > value.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if (unsigned(value[offset + i]) != expected[i]) return false;
        }
        return true;
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    public record Dimensions(int width, int height) {
    }
}
