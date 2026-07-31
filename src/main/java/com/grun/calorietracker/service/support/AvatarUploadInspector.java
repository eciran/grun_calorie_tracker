package com.grun.calorietracker.service.support;

import com.grun.calorietracker.config.ProfileMediaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AvatarUploadInspector {
    private final ProfileMediaProperties properties;

    public InspectedAvatar inspect(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Avatar image is required.");
        }
        if (file.getSize() > properties.getAvatar().getMaxUploadBytes()) {
            throw new IllegalArgumentException("Avatar image exceeds the configured upload limit.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        Set<String> allowed = Arrays.stream(properties.getAvatar().getAllowedContentTypes().split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        if (!allowed.contains(contentType)) {
            throw new IllegalArgumentException("Avatar image content type is not allowed.");
        }
        byte[] bytes = read(file);
        if (!matchesSignature(bytes, contentType)) {
            throw new IllegalArgumentException("Avatar image content does not match its declared type.");
        }
        return new InspectedAvatar(bytes, contentType, extension(contentType), sha256(bytes));
    }

    private byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Avatar image could not be read.", exception);
        }
    }

    private boolean matchesSignature(byte[] value, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> matches(value, 0xFF, 0xD8, 0xFF);
            case "image/png" -> matches(value, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/webp" -> matches(value, 0x52, 0x49, 0x46, 0x46)
                    && value.length >= 12 && value[8] == 0x57 && value[9] == 0x45
                    && value[10] == 0x42 && value[11] == 0x50;
            default -> false;
        };
    }

    private boolean matches(byte[] value, int... expected) {
        if (value == null || value.length < expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if ((value[index] & 0xFF) != expected[index]) return false;
        }
        return true;
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    public record InspectedAvatar(byte[] bytes, String contentType, String extension, String sha256) {
    }
}
