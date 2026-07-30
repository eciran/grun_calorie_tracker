package com.grun.calorietracker.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@ConditionalOnProperty(prefix = "grun.food-contribution-storage", name = "provider", havingValue = "S3")
public class FoodContributionS3Config {
    @Bean
    S3Client foodContributionS3Client(FoodContributionStorageProperties properties) {
        validate(properties);
        var builder = S3Client.builder()
                .region(region(properties))
                .serviceConfiguration(serviceConfiguration(properties));
        applyEndpoint(properties, builder);
        applyCredentials(properties, builder);
        return builder.build();
    }

    @Bean
    S3Presigner foodContributionS3Presigner(FoodContributionStorageProperties properties) {
        validate(properties);
        var builder = S3Presigner.builder()
                .region(region(properties))
                .serviceConfiguration(serviceConfiguration(properties));
        if (hasText(properties.getS3().getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getS3().getEndpoint().trim()));
        }
        if (hasText(properties.getS3().getAccessKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    properties.getS3().getAccessKey().trim(),
                    properties.getS3().getSecretKey().trim()
            )));
        }
        return builder.build();
    }

    private void validate(FoodContributionStorageProperties properties) {
        if (!hasText(properties.getS3().getBucket())) {
            throw new IllegalStateException("GRUN_FOOD_CONTRIBUTION_S3_BUCKET is required when S3-compatible evidence storage is enabled.");
        }
        if (!hasText(properties.getS3().getRegion())) {
            throw new IllegalStateException("GRUN_FOOD_CONTRIBUTION_S3_REGION is required when S3-compatible evidence storage is enabled.");
        }
        boolean accessKeyPresent = hasText(properties.getS3().getAccessKey());
        boolean secretKeyPresent = hasText(properties.getS3().getSecretKey());
        if (accessKeyPresent != secretKeyPresent) {
            throw new IllegalStateException("S3-compatible access key and secret key must be configured together.");
        }
        if (properties.getUploadUrlTtl().isZero() || properties.getUploadUrlTtl().isNegative()
                || properties.getAdminReadUrlTtl().isZero() || properties.getAdminReadUrlTtl().isNegative()) {
            throw new IllegalStateException("Signed URL TTL values must be positive.");
        }
    }

    private Region region(FoodContributionStorageProperties properties) {
        return Region.of(properties.getS3().getRegion().trim());
    }

    private S3Configuration serviceConfiguration(FoodContributionStorageProperties properties) {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(properties.getS3().isPathStyleAccess())
                .build();
    }

    private void applyEndpoint(FoodContributionStorageProperties properties, S3ClientBuilder builder) {
        if (hasText(properties.getS3().getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getS3().getEndpoint().trim()));
        }
    }

    private void applyCredentials(FoodContributionStorageProperties properties, S3ClientBuilder builder) {
        if (hasText(properties.getS3().getAccessKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    properties.getS3().getAccessKey().trim(),
                    properties.getS3().getSecretKey().trim()
            )));
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
