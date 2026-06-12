package com.grun.calorietracker.config;

import com.grun.calorietracker.enums.PushProvider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "grun.push")
public class PushProperties {
    private boolean enabled = false;
    private PushProvider provider = PushProvider.LOG;
    private Expo expo = new Expo();
    private Fcm fcm = new Fcm();
    private Onesignal onesignal = new Onesignal();

    @Data
    public static class Expo {
        private String url = "https://exp.host/--/api/v2/push/send";
        private String accessToken;
    }

    @Data
    public static class Fcm {
        private String projectId;
        private String accessToken;
        private String credentialsJson;
    }

    @Data
    public static class Onesignal {
        private String appId;
        private String apiKey;
    }
}
