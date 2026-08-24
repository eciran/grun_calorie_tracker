package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.ProfileMediaProperties;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.UserAvatarService;
import com.grun.calorietracker.service.media.MediaNamespace;
import com.grun.calorietracker.service.media.MediaObjectStorage;
import com.grun.calorietracker.service.media.MediaStorageKeyPolicy;
import com.grun.calorietracker.service.support.AvatarUploadInspector;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class UserAvatarServiceImpl implements UserAvatarService {

    private final ProfileMediaProperties properties;
    private final UserRepository userRepository;
    private final MediaObjectStorage mediaStorage;
    private final MediaStorageKeyPolicy keyPolicy;
    private final AvatarUploadInspector uploadInspector;

    @Override
    public UserProfileDto uploadAvatar(String email, MultipartFile file) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        var avatar = uploadInspector.inspect(file);
        String owner = "u" + user.getId();
        String storageKey = keyPolicy.create(MediaNamespace.PROFILE_AVATAR, owner, avatar.extension());
        String previousUrl = user.getAvatarUrl();

        mediaStorage.store(storageKey, avatar.bytes(), avatar.contentType(), avatar.sha256());
        try {
            user.setAvatarUrl(publicUrl(publicToken(storageKey, owner)));
            UserProfileDto result = toProfileDto(userRepository.save(user));
            deleteExistingAvatar(previousUrl);
            return result;
        } catch (RuntimeException exception) {
            safelyDelete(storageKey);
            throw exception;
        }
    }

    @Override
    public UserProfileDto deleteAvatar(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        String previousUrl = user.getAvatarUrl();
        user.setAvatarUrl(null);
        UserProfileDto result = toProfileDto(userRepository.save(user));
        deleteExistingAvatar(previousUrl);
        return result;
    }

    @Override
    public Resource loadAvatar(String filename) {
        String storageKey = storageKeyFromPublicToken(filename);
        if (storageKey != null) {
            mediaStorage.inspect(storageKey);
            return new ByteArrayResource(mediaStorage.readBounded(
                    storageKey, properties.getAvatar().getMaxUploadBytes()));
        }
        return loadLegacyAvatar(filename);
    }

    private Resource loadLegacyAvatar(String filename) {
        validateFilename(filename);
        Path target = storageRoot().resolve(filename).normalize();
        ensureInsideStorage(target);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new IllegalArgumentException("Avatar image was not found.");
        }
        try {
            return new UrlResource(target.toUri());
        } catch (MalformedURLException exception) {
            throw new IllegalArgumentException("Avatar image could not be loaded.");
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
        if (filename == null) return;
        String storageKey = storageKeyFromPublicToken(filename);
        if (storageKey != null) {
            safelyDelete(storageKey);
            return;
        }
        Path target = storageRoot().resolve(filename).normalize();
        try {
            ensureInsideStorage(target);
            Files.deleteIfExists(target);
        } catch (IOException | IllegalArgumentException ignored) {
            // An already missing legacy avatar must not fail a profile update.
        }
    }

    private void safelyDelete(String storageKey) {
        try {
            mediaStorage.delete(storageKey);
        } catch (RuntimeException ignored) {
            // Cleanup can be retried without breaking the profile operation.
        }
    }

    private String publicToken(String storageKey, String owner) {
        String prefix = keyPolicy.prefix(MediaNamespace.PROFILE_AVATAR) + "/" + owner + "/";
        if (!storageKey.startsWith(prefix)) {
            throw new IllegalArgumentException("Avatar storage key is invalid.");
        }
        return owner + "~" + storageKey.substring(prefix.length());
    }

    private String storageKeyFromPublicToken(String token) {
        if (token == null || !token.matches("u\\d+~[a-fA-F0-9-]{36}\\.(jpg|jpeg|png|webp)")) {
            return null;
        }
        int separator = token.indexOf('~');
        String owner = token.substring(0, separator);
        String objectName = token.substring(separator + 1);
        return keyPolicy.requireManaged(
                keyPolicy.prefix(MediaNamespace.PROFILE_AVATAR) + "/" + owner + "/" + objectName);
    }

    private String filenameFromAvatarUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) return null;
        String marker = "/api/v1/users/avatars/";
        int markerIndex = avatarUrl.indexOf(marker);
        if (markerIndex < 0) return null;
        String filename = avatarUrl.substring(markerIndex + marker.length());
        if (filename.isBlank() || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return null;
        }
        return filename;
    }

    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.contains("..")
                || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Avatar filename is invalid.");
        }
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
