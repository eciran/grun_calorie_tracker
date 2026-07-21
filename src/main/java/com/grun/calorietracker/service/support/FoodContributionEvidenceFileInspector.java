package com.grun.calorietracker.service.support;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.service.evidence.FoodContributionEvidenceStorage.InspectedEvidence;
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
public class FoodContributionEvidenceFileInspector {
    private final FoodContributionStorageProperties properties;

    public InspectedEvidence inspect(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A product label image is required.");
        }
        if (file.getSize() > properties.getMaxUploadBytes()) {
            throw new IllegalArgumentException("Product label image exceeds the configured upload limit.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        Set<String> allowed = Arrays.stream(properties.getAllowedContentTypes().split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        if (!allowed.contains(contentType)) {
            throw new IllegalArgumentException("Product label image content type is not allowed.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Product label image could not be read.");
        }
        validateSignature(bytes, contentType);
        return new InspectedEvidence(bytes, contentType, extension(contentType), sha256(bytes));
    }

    private void validateSignature(byte[] value, String contentType) {
        boolean valid = switch (contentType) {
            case "image/jpeg" -> matches(value, 0xFF, 0xD8, 0xFF);
            case "image/png" -> matches(value, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/webp" -> matches(value, 0x52, 0x49, 0x46, 0x46)
                    && value.length >= 12
                    && value[8] == 0x57 && value[9] == 0x45 && value[10] == 0x42 && value[11] == 0x50;
            default -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException("Product label image content does not match its declared type.");
        }
    }

    private boolean matches(byte[] value, int... expected) {
        if (value == null || value.length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if ((value[index] & 0xFF) != expected[index]) {
                return false;
            }
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
}
