package com.grun.calorietracker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "grun.profile-media")
public class ProfileMediaProperties {
    private Avatar avatar = new Avatar();

    @Data
    public static class Avatar {
        private long maxUploadBytes = 2 * 1024 * 1024;
        private String allowedContentTypes = "image/jpeg,image/png,image/webp";
        private String storageDirectory = "storage/profile-avatars";
        private String publicBaseUrl = "https://api.grun.app";
    }
}
