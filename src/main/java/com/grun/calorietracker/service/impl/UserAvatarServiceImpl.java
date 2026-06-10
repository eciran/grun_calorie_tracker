package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.ProfileMediaProperties;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.UserAvatarService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserAvatarServiceImpl implements UserAvatarService {

    private final ProfileMediaProperties properties;
    private final UserRepository userRepository;

    @Override
    public UserProfileDto uploadAvatar(String email, MultipartFile file) {
        validate(file);
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        String extension = extension(file.getOriginalFilename(), file.getContentType());
        String filename = "u" + user.getId() + "-" + UUID.randomUUID() + extension;
        Path target = storageRoot().resolve(filename).normalize();
        ensureInsideStorage(target);

        try {
            Files.createDirectories(storageRoot());
            file.transferTo(target);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Avatar image could not be stored.");
        }

        deleteExistingAvatar(user.getAvatarUrl());
        user.setAvatarUrl(publicUrl(filename));
        return toProfileDto(userRepository.save(user));
    }

    @Override
    public UserProfileDto deleteAvatar(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        deleteExistingAvatar(user.getAvatarUrl());
        user.setAvatarUrl(null);
        return toProfileDto(userRepository.save(user));
    }

    @Override
    public Resource loadAvatar(String filename) {
        if (filename == null || filename.isBlank() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Avatar filename is invalid.");
        }
        Path target = storageRoot().resolve(filename).normalize();
        ensureInsideStorage(target);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new IllegalArgumentException("Avatar image was not found.");
        }
        try {
            return new UrlResource(target.toUri());
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("Avatar image could not be loaded.");
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Avatar image is required.");
        }
        if (file.getSize() > properties.getAvatar().getMaxUploadBytes()) {
            throw new IllegalArgumentException("Avatar image exceeds the configured upload limit.");
        }
        String contentType = file.getContentType();
        Set<String> allowedTypes = Arrays.stream(properties.getAvatar().getAllowedContentTypes().split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        if (contentType == null || !allowedTypes.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Avatar image content type is not allowed.");
        }
    }

    private Path storageRoot() {
        return Path.of(properties.getAvatar().getStorageDirectory()).toAbsolutePath().normalize();
    }

    private void ensureInsideStorage(Path target) {
        if (!target.startsWith(storageRoot())) {
            throw new IllegalArgumentException("Avatar path is invalid.");
        }
    }

    private String publicUrl(String filename) {
        String baseUrl = properties.getAvatar().getPublicBaseUrl().replaceAll("/+$", "");
        return baseUrl + "/api/v1/users/avatars/" + filename;
    }

    private void deleteExistingAvatar(String avatarUrl) {
        String filename = filenameFromAvatarUrl(avatarUrl);
        if (filename == null) {
            return;
        }
        Path target = storageRoot().resolve(filename).normalize();
        try {
            ensureInsideStorage(target);
            Files.deleteIfExists(target);
        } catch (IOException | IllegalArgumentException ignored) {
            // The profile update should not fail because an old local file is already gone.
        }
    }

    private String filenameFromAvatarUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return null;
        }
        String marker = "/api/v1/users/avatars/";
        int markerIndex = avatarUrl.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        String filename = avatarUrl.substring(markerIndex + marker.length());
        if (filename.isBlank() || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return null;
        }
        return filename;
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

    private UserProfileDto toProfileDto(UserEntity user) {
        UserProfileDto dto = new UserProfileDto();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setName(user.getName());
        dto.setAge(user.getAge());
        dto.setGender(user.getGender());
        dto.setHeight(user.getHeight());
        dto.setWeight(user.getWeight());
        dto.setBodyFat(user.getBodyFatPercentage());
        dto.setBmi(user.getBmi());
        dto.setRole(user.getRole());
        dto.setEmailVerified(user.getEmailVerified());
        dto.setPasswordSet(user.getPasswordSet());
        dto.setMarketRegion(user.getMarketRegion());
        dto.setPreferredLanguage(user.getPreferredLanguage());
        dto.setAvatarUrl(user.getAvatarUrl());
        return dto;
    }
}
