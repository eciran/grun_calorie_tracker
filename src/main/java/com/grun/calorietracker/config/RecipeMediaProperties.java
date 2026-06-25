package com.grun.calorietracker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "grun.recipe-media")
public class RecipeMediaProperties {
    private Image image = new Image();

    @Data
    public static class Image {
        private long maxUploadBytes = 4 * 1024 * 1024;
        private String allowedContentTypes = "image/jpeg,image/png,image/webp";
        private String storageDirectory = "storage/recipe-images";
        private String publicBaseUrl = "http://localhost:8080";
    }
}