package com.grun.calorietracker.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class FoodContributionRetentionConfigurationTest {
    private final FoodContributionS3Config config = new FoodContributionS3Config();

    @Test
    void rejectsPendingEvidenceRetentionBeyondTwentyEightDays() {
        FoodContributionStorageProperties properties = validProperties();
        properties.setPendingRetentionDays(29);

        assertThrows(IllegalStateException.class, () -> config.foodContributionS3Client(properties));
    }

    @Test
    void rejectsApprovedEvidenceDelayBeyondTwentyFourHours() {
        FoodContributionStorageProperties properties = validProperties();
        properties.setApprovedEvidenceDeletionDelay(Duration.ofHours(25));

        assertThrows(IllegalStateException.class, () -> config.foodContributionS3Client(properties));
    }

    @Test
    void rejectsRejectedEvidenceRetentionBeyondSevenDays() {
        FoodContributionStorageProperties properties = validProperties();
        properties.setRejectedEvidenceRetention(Duration.ofDays(8));

        assertThrows(IllegalStateException.class, () -> config.foodContributionS3Client(properties));
    }

    private FoodContributionStorageProperties validProperties() {
        FoodContributionStorageProperties properties = new FoodContributionStorageProperties();
        properties.getS3().setEndpoint("https://example.invalid");
        properties.getS3().setBucket("private-product-evidence");
        properties.getS3().setRegion("eu-west-1");
        properties.getS3().setAccessKey("test-access-key");
        properties.getS3().setSecretKey("test-secret-key");
        return properties;
    }
}
