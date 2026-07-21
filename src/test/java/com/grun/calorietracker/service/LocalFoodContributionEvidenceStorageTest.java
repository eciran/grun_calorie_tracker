package com.grun.calorietracker.service;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.service.evidence.FoodContributionEvidenceStorage.InspectedEvidence;
import com.grun.calorietracker.service.evidence.LocalFoodContributionEvidenceStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalFoodContributionEvidenceStorageTest {

    @TempDir
    Path directory;

    @Test
    void storeAndLoad_roundTripsPrivateEvidence() {
        FoodContributionStorageProperties properties = new FoodContributionStorageProperties();
        properties.setStorageDirectory(directory.toString());
        LocalFoodContributionEvidenceStorage storage = new LocalFoodContributionEvidenceStorage(properties);
        byte[] bytes = {1, 2, 3};

        var stored = storage.store(7L, "8691234567890", new InspectedEvidence(bytes, "image/jpeg", ".jpg", "a".repeat(64)));
        var loaded = storage.load(stored.storageKey());

        assertArrayEquals(bytes, loaded.bytes());
    }

    @Test
    void load_rejectsPathTraversal() {
        FoodContributionStorageProperties properties = new FoodContributionStorageProperties();
        properties.setStorageDirectory(directory.toString());
        LocalFoodContributionEvidenceStorage storage = new LocalFoodContributionEvidenceStorage(properties);

        assertThrows(IllegalArgumentException.class, () -> storage.load("../secret.jpg"));
    }
}
