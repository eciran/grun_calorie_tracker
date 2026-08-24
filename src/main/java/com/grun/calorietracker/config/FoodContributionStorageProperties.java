package com.grun.calorietracker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "grun.food-contribution-storage")
public class FoodContributionStorageProperties {
    private String provider = "LOCAL";
    private long maxUploadBytes = 6 * 1024 * 1024;
    private long maxDecodedPixels = 24_000_000;
    private String allowedContentTypes = "image/jpeg,image/png,image/webp";
    private String storageDirectory = "storage/food-contribution-evidence";
    private String privateBaseUrl = "https://api.grun.app";
    private Duration uploadSessionTtl = Duration.ofMinutes(20);
    private Duration uploadUrlTtl = Duration.ofMinutes(10);
    private Duration adminReadUrlTtl = Duration.ofMinutes(5);
    private int pendingRetentionDays = 28;
    private Duration approvedEvidenceDeletionDelay = Duration.ofHours(24);
    private Duration rejectedEvidenceRetention = Duration.ofDays(7);
    private int cleanupBatchSize = 50;
    private S3 s3 = new S3();

    @Data
    public static class S3 {
        private String endpoint = "";
        private String jurisdiction = "";
        private String accessKey = "";
        private String secretKey = "";
        private String bucket = "";
        private String region = "eu-west-1";
        private String prefix = "pending/product-intakes";
        private boolean pathStyleAccess = false;
    }
}
