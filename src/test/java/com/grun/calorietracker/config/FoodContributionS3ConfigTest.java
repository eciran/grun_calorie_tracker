package com.grun.calorietracker.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoodContributionS3ConfigTest {
    private final FoodContributionS3Config config = new FoodContributionS3Config();

    @Test
    void createsDedicatedClientAndPresignerForGenericS3Endpoint() {
        FoodContributionStorageProperties properties = validProperties();
        try (S3Client client = config.foodContributionS3Client(properties);
             S3Presigner presigner = config.foodContributionS3Presigner(properties)) {
            assertDoesNotThrow(client::serviceName);
            assertDoesNotThrow(presigner::close);
        }
    }

    @Test
    void allowsDefaultCredentialChainWhenStaticCredentialsAreAbsent() {
        FoodContributionStorageProperties properties = validProperties();
        properties.getS3().setAccessKey("");
        properties.getS3().setSecretKey("");
        try (S3Client client = config.foodContributionS3Client(properties)) {
            assertDoesNotThrow(client::serviceName);
        }
    }

    @Test
    void rejectsPartialStaticCredentials() {
        FoodContributionStorageProperties properties = validProperties();
        properties.getS3().setSecretKey("");
        assertThrows(IllegalStateException.class, () -> config.foodContributionS3Client(properties));
    }

    @Test
    void rejectsNonPositiveSignedUrlTtl() {
        FoodContributionStorageProperties properties = validProperties();
        properties.setUploadUrlTtl(Duration.ZERO);
        assertThrows(IllegalStateException.class, () -> config.foodContributionS3Presigner(properties));
    }

    private FoodContributionStorageProperties validProperties() {
        FoodContributionStorageProperties properties = new FoodContributionStorageProperties();
        properties.getS3().setEndpoint("https://example.invalid");
        properties.getS3().setBucket("private-product-evidence");
        properties.getS3().setRegion("auto");
        properties.getS3().setAccessKey("test-access-key");
        properties.getS3().setSecretKey("test-secret-key");
        return properties;
    }
}
