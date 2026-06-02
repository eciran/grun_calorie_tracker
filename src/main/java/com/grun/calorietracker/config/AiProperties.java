package com.grun.calorietracker.config;

import com.grun.calorietracker.enums.AiProvider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "grun.ai")
public class AiProperties {
    private boolean enabled = false;
    private AiProvider provider = AiProvider.DISABLED;
    private String model = "not-configured";
    private int maxHistoryLimit = 30;
    private Safety safety = new Safety();
    private Photo photo = new Photo();
    private HttpJson httpJson = new HttpJson();

    @Data
    public static class Safety {
        private boolean enabled = true;
        private int maxItemCalories = 3000;
        private int maxTotalCalories = 6000;
    }

    @Data
    public static class Photo {
        private int maxImageReferenceLength = 2048;
        private String allowedReferencePrefixes = "s3://,https://";
        private long maxUploadBytes = 5 * 1024 * 1024;
        private String allowedContentTypes = "image/jpeg,image/png,image/webp";
        private Duration referenceTtl = Duration.ofMinutes(30);
        private long cleanupIntervalMs = 3_600_000;
        private String storageDirectory = "storage/ai-meal-photos";
        private String publicBaseUrl = "https://api.grun.app";
    }

    @Data
    public static class HttpJson {
        private String endpoint = "";
        private String apiKey = "";
        private Duration timeout = Duration.ofSeconds(30);
    }
}
