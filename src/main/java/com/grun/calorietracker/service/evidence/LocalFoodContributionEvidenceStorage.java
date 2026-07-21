package com.grun.calorietracker.service.evidence;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "grun.food-contribution-storage", name = "provider", havingValue = "LOCAL", matchIfMissing = true)
public class LocalFoodContributionEvidenceStorage implements FoodContributionEvidenceStorage {
    private final FoodContributionStorageProperties properties;

    @Override
    public StoredEvidence store(Long userId, String barcode, InspectedEvidence evidence) {
        String key = "u" + userId + "/" + barcode + "-" + UUID.randomUUID() + evidence.extension();
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, evidence.bytes());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Product label evidence could not be stored.");
        }
        return new StoredEvidence(key, evidence.checksum(), evidence.contentType(), evidence.bytes().length);
    }

    @Override
    public EvidenceContent load(String storageKey) {
        Path target = resolve(storageKey);
        if (!Files.isRegularFile(target)) {
            throw new IllegalArgumentException("Product label evidence was not found.");
        }
        try {
            return new EvidenceContent(Files.readAllBytes(target), Files.probeContentType(target));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Product label evidence could not be loaded.");
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ignored) {
            // A failed database write remains the primary failure; orphan cleanup can retry later.
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("..") || storageKey.startsWith("/") || storageKey.startsWith("\\")) {
            throw new IllegalArgumentException("Product label storage key is invalid.");
        }
        Path root = Path.of(properties.getStorageDirectory()).toAbsolutePath().normalize();
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Product label storage key is invalid.");
        }
        return target;
    }
}
