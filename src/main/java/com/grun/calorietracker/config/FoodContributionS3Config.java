package com.grun.calorietracker.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(prefix = "grun.food-contribution-storage", name = "provider", havingValue = "S3")
public class FoodContributionS3Config {
    @Bean
    S3Client foodContributionS3Client(FoodContributionStorageProperties properties) {
        if (properties.getS3().getBucket() == null || properties.getS3().getBucket().isBlank()) {
            throw new IllegalStateException("GRUN_FOOD_CONTRIBUTION_S3_BUCKET is required when S3 evidence storage is enabled.");
        }
        if (properties.getS3().getRegion() == null || properties.getS3().getRegion().isBlank()) {
            throw new IllegalStateException("GRUN_FOOD_CONTRIBUTION_S3_REGION is required when S3 evidence storage is enabled.");
        }
        return S3Client.builder().region(Region.of(properties.getS3().getRegion())).build();
    }
}
