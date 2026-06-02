package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiPhotoReferenceDto;
import com.grun.calorietracker.service.AiPhotoReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiPhotoReferenceServiceImpl implements AiPhotoReferenceService {

    private final AiProperties properties;

    @Override
    public AiPhotoReferenceDto createReference(String email, MultipartFile file) {
        validate(file);
        long expiresAtMillis = Instant.now().plus(properties.getPhoto().getReferenceTtl()).toEpochMilli();
        String extension = extension(file.getOriginalFilename(), file.getContentType());
        String token = expiresAtMillis + "-" + UUID.randomUUID() + extension;
        Path target = storageRoot().resolve(token).normalize();
        ensureInsideStorage(target);

        try {
            Files.createDirectories(storageRoot());
            file.transferTo(target);
        } catch (IOException ex) {
            throw new IllegalArgumentException("AI meal photo could not be stored.");
        }

        String baseUrl = properties.getPhoto().getPublicBaseUrl().replaceAll("/+$", "");
        String imageReference = baseUrl + "/api/v1/ai/meal-drafts/photo-references/" + token;
        LocalDateTime expiresAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(expiresAtMillis), ZoneOffset.UTC);
        return new AiPhotoReferenceDto(imageReference, token, expiresAt);
    }

    @Override
    public Resource loadReference(String token) {
        if (token == null || token.isBlank() || token.contains("..") || token.contains("/") || token.contains("\\")) {
            throw new IllegalArgumentException("Photo reference is invalid.");
        }
        long expiresAtMillis = parseExpiresAt(token);
        if (Instant.now().toEpochMilli() > expiresAtMillis) {
            deleteQuietly(storageRoot().resolve(token).normalize());
            throw new IllegalArgumentException("Photo reference has expired.");
        }
        Path target = storageRoot().resolve(token).normalize();
        ensureInsideStorage(target);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new IllegalArgumentException("Photo reference was not found.");
        }
        try {
            return new UrlResource(target.toUri());
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("Photo reference could not be loaded.");
        }
    }

    @Override
    @Scheduled(fixedDelayString = "${grun.ai.photo.cleanup-interval-ms:3600000}")
    public long cleanupExpiredReferences() {
        Path root = storageRoot();
        if (!Files.exists(root)) {
            return 0;
        }
        long now = Instant.now().toEpochMilli();
        try (var stream = Files.list(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> isExpired(path.getFileName().toString(), now))
                    .mapToLong(path -> deleteQuietly(path) ? 1L : 0L)
                    .sum();
        } catch (IOException ex) {
            return 0;
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("AI meal photo is required.");
        }
        if (file.getSize() > properties.getPhoto().getMaxUploadBytes()) {
            throw new IllegalArgumentException("AI meal photo exceeds the configured upload limit.");
        }
        String contentType = file.getContentType();
        Set<String> allowedTypes = Arrays.stream(properties.getPhoto().getAllowedContentTypes().split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        if (contentType == null || !allowedTypes.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("AI meal photo content type is not allowed.");
        }
    }

    private Path storageRoot() {
        return Path.of(properties.getPhoto().getStorageDirectory()).toAbsolutePath().normalize();
    }

    private void ensureInsideStorage(Path target) {
        if (!target.startsWith(storageRoot())) {
            throw new IllegalArgumentException("Photo reference path is invalid.");
        }
    }

    private long parseExpiresAt(String token) {
        int separator = token.indexOf('-');
        if (separator <= 0) {
            throw new IllegalArgumentException("Photo reference is invalid.");
        }
        try {
            return Long.parseLong(token.substring(0, separator));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Photo reference is invalid.");
        }
    }

    private boolean isExpired(String token, long now) {
        try {
            return now > parseExpiresAt(token);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean deleteQuietly(Path target) {
        try {
            ensureInsideStorage(target);
            return Files.deleteIfExists(target);
        } catch (IOException | IllegalArgumentException ex) {
            return false;
        }
    }

    private String extension(String originalFilename, String contentType) {
        String filename = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename);
        String extension = "";
        int dot = filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            extension = filename.substring(dot).toLowerCase(Locale.ROOT);
        }
        if (Set.of(".jpg", ".jpeg", ".png", ".webp").contains(extension)) {
            return extension;
        }
        return switch (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
