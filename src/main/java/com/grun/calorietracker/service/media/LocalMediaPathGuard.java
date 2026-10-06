package com.grun.calorietracker.service.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class LocalMediaPathGuard {
    private LocalMediaPathGuard() {}

    public static void requireInside(Path root, Path target) {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        Path absoluteTarget = target.toAbsolutePath().normalize();
        if (!absoluteTarget.startsWith(absoluteRoot)) throw new IllegalArgumentException("Media path is invalid.");
        try {
            for (Path path = absoluteTarget; path != null; path = path.getParent()) {
                if (Files.isSymbolicLink(path)) throw new IllegalArgumentException("Media symlinks are not allowed.");
                if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !path.toRealPath().equals(path)) {
                    throw new IllegalArgumentException("Media redirected paths are not allowed.");
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Media path could not be verified.", exception);
        }
    }

    public static byte[] readBounded(Path root, Path target, long maximumBytes) throws IOException {
        if (maximumBytes <= 0 || maximumBytes >= Integer.MAX_VALUE) throw new IllegalArgumentException("Media read limit is invalid.");
        requireInside(root, target);
        try (var input = Files.newInputStream(target, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] content = input.readNBytes((int) maximumBytes + 1);
            if (content.length > maximumBytes) throw new IllegalArgumentException("Stored media exceeds the configured read limit.");
            return content;
        }
    }
}
