package com.grun.calorietracker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "grun.media-storage")
public class MediaStorageProperties {
    private String provider = "LOCAL";
    private String localDirectory = "storage/media";
    private String rootPrefix = "grun";
    private String publicBaseUrl = "https://api.grun.app";
    private S3 s3 = new S3();

    @Data
    public static class S3 {
        private String endpoint = "";
        private String accessKey = "";
        private String secretKey = "";
        private String bucket = "";
        private String region = "eu-west-1";
        private boolean pathStyleAccess = false;
    }
}
