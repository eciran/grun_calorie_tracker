package com.grun.calorietracker.service.media;

import com.grun.calorietracker.config.MediaStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MediaStorageKeyPolicy {
    private final MediaStorageProperties properties;

    public String create(MediaNamespace namespace, String ownerSegment, String extension) {
        String owner = safeSegment(ownerSegment, "Media owner segment is invalid.");
        return prefix(namespace) + "/" + owner + "/" + UUID.randomUUID() + normalizeExtension(extension);
    }

    public String requireManaged(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.startsWith("/")
                || storageKey.contains("\\") || storageKey.contains("..")) {
            throw new IllegalArgumentException("Media storage key is invalid.");
        }
        String root = normalizedRoot();
        boolean managed = Arrays.stream(MediaNamespace.values())
                .anyMatch(namespace -> storageKey.startsWith(root + "/" + namespace.path() + "/"));
        if (!managed) {
            throw new IllegalArgumentException("Media storage key is outside managed namespaces.");
        }
        return storageKey;
    }

    public String prefix(MediaNamespace namespace) {
        return normalizedRoot() + "/" + namespace.path();
    }

    private String normalizedRoot() {
        String value = properties.getRootPrefix();
        String normalized = value == null ? "" : value.trim().replaceAll("^/+|/+$", "");
        return normalized.isBlank() ? "grun" : safeSegment(normalized, "Media root prefix is invalid.");
    }

    private String safeSegment(String value, String message) {
        if (value == null || value.isBlank() || value.contains("..") || value.contains("/") || value.contains("\\")) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeExtension(String extension) {
        if (extension == null || !extension.matches("\\.[a-zA-Z0-9]{2,5}")) {
            throw new IllegalArgumentException("Media file extension is invalid.");
        }
        return extension.toLowerCase(Locale.ROOT);
    }
}
