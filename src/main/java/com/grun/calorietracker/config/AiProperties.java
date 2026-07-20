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
    private String promptVersion = "ai-prompt-v1";
    private int maxHistoryLimit = 30;
    private Safety safety = new Safety();
    private Photo photo = new Photo();
    private HttpJson httpJson = new HttpJson();
    private OpenAi openai = new OpenAi();
    private RecipeImageModeration recipeImageModeration = new RecipeImageModeration();
    private Monitoring monitoring = new Monitoring();

    @Data
    public static class Safety {
        private boolean enabled = true;
        private int maxItemCalories = 3000;
        private int maxTotalCalories = 6000;
    }

    @Data
    public static class Photo {
        private int maxImageReferenceLength = 2048;
        private String allowedReferencePrefixes = "s3://grun-meals/";
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

    @Data
    public static class OpenAi {
        private String apiKey = "";
        private String baseUrl = "https://api.openai.com/v1/responses";
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration timeout = Duration.ofSeconds(120);
        private int maxOutputTokens = 12000;
        private boolean repairEnabled = true;
        private int maxRepairAttempts = 1;
        private double inputTokenCostPer1m = 0;
        private double outputTokenCostPer1m = 0;
        private String costCurrency = "USD";
    }

    @Data
    public static class Monitoring {
        private int minRequestsForAlert = 5;
        private double failureRateThreshold = 0.20;
        private double rejectionRateThreshold = 0.40;
        private long maxTokensPerWindow = 1_000_000;
        private double maxEstimatedCostPerCurrency = 20.0;
    }

    @Data
    public static class RecipeImageModeration {
        private boolean enabled = false;
        private String endpoint = "";
        private String apiKey = "";
        private Duration timeout = Duration.ofSeconds(15);
        private double rejectThreshold = 0.85;
        private double approveThreshold = 0.95;
    }
}

