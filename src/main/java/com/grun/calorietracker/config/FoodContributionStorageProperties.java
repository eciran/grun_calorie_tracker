package com.grun.calorietracker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "grun.food-contribution-storage")
public class FoodContributionStorageProperties {
    private String provider = "LOCAL";
    private long maxUploadBytes = 6 * 1024 * 1024;
    private String allowedContentTypes = "image/jpeg,image/png,image/webp";
    private String storageDirectory = "storage/food-contribution-evidence";
    private String privateBaseUrl = "https://api.grun.app";
    private S3 s3 = new S3();

    @Data
    public static class S3 {
        private String bucket = "";
        private String region = "eu-west-1";
        private String prefix = "product-contributions";
    }
}
