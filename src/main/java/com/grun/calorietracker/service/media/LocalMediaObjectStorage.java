package com.grun.calorietracker.service.media;

import com.grun.calorietracker.config.MediaStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "grun.media-storage", name = "provider", havingValue = "LOCAL", matchIfMissing = true)
public class LocalMediaObjectStorage implements MediaObjectStorage {
    private final MediaStorageProperties properties;
    private final MediaStorageKeyPolicy keyPolicy;

    @Override
    public StoredMediaObject store(String storageKey, byte[] content, String contentType, String sha256) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Media content is required.");
        }
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            LocalMediaPathGuard.requireInside(root(), target);
            LocalMediaPathGuard.requireInside(root(), checksumPath(target));
            Files.write(target, content, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.LinkOption.NOFOLLOW_LINKS);
            Files.writeString(checksumPath(target), sha256 == null ? "" : sha256,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS);
            return new StoredMediaObject(storageKey, contentType, content.length, sha256);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Media object could not be stored.", exception);
        }
    }

    @Override
    public StoredMediaObject inspect(String storageKey) {
        Path target = requireFile(storageKey);
        try {
            LocalMediaPathGuard.requireInside(root(), checksumPath(target));
            String checksum = Files.exists(checksumPath(target), java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    ? new String(LocalMediaPathGuard.readBounded(root(), checksumPath(target), 256), java.nio.charset.StandardCharsets.UTF_8) : null;
            return new StoredMediaObject(storageKey, Files.probeContentType(target), Files.size(target), checksum);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Media object could not be inspected.", exception);
        }
    }

    @Override
    public byte[] readBounded(String storageKey, long maximumBytes) {
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("Maximum media read size must be positive.");
        }
        Path target = requireFile(storageKey);
        try {
            if (Files.size(target) > maximumBytes) {
                throw new IllegalArgumentException("Stored media exceeds the configured read limit.");
            }
            return LocalMediaPathGuard.readBounded(root(), target, maximumBytes);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Media object could not be read.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolve(storageKey);
        LocalMediaPathGuard.requireInside(root(), checksumPath(target));
        try {
            Files.deleteIfExists(target);
            Files.deleteIfExists(checksumPath(target));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Media object could not be deleted.", exception);
        }
    }

    private Path requireFile(String storageKey) {
        Path target = resolve(storageKey);
        if (!Files.isRegularFile(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Media object was not found.");
        }
        return target;
    }

    private Path resolve(String storageKey) {
        String managedKey = keyPolicy.requireManaged(storageKey);
        Path root = root();
        Path target = root.resolve(managedKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Media storage key is invalid.");
        }
        LocalMediaPathGuard.requireInside(root, target);
        return target;
    }

    private Path root() {
        return Path.of(properties.getLocalDirectory()).toAbsolutePath().normalize();
    }

    private Path checksumPath(Path target) {
        return target.resolveSibling(target.getFileName() + ".sha256");
    }
}
