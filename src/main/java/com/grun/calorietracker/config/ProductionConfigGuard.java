package com.grun.calorietracker.config;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("prod")
public class ProductionConfigGuard {

    private static final String LOCAL_DEV_JWT_SECRET =
            "Q0hBTkdFX01FX0xPQ0FMX0RFVkVMT1BNRU5UX1NFQ1JFVF9LRVlfMTIzNDU2Nzg5MA==";

    private final Environment environment;

    public ProductionConfigGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateProductionConfiguration() {
        List<String> errors = new ArrayList<>();

        requireSecret(errors, "JWT_SECRET", "jwt.secret");
        rejectValue(errors, "JWT_SECRET", "jwt.secret", LOCAL_DEV_JWT_SECRET);
        requireSecret(errors, "GRUN_REVENUECAT_WEBHOOK_AUTHORIZATION", "grun.revenuecat.webhook-authorization");

        String mailProvider = environment.getProperty("grun.mail.provider", "LOG");
        if ("LOG".equalsIgnoreCase(mailProvider)) {
            errors.add("GRUN_MAIL_PROVIDER must not be LOG in prod.");
        }
        if ("BREVO".equalsIgnoreCase(mailProvider)) {
            requireSecret(errors, "GRUN_BREVO_API_KEY", "grun.mail.brevo.api-key");
        }

        boolean pushEnabled = environment.getProperty("grun.push.enabled", Boolean.class, false);
        String pushProvider = environment.getProperty("grun.push.provider", "LOG");
        if (pushEnabled) {
            if ("LOG".equalsIgnoreCase(pushProvider)) {
                errors.add("GRUN_PUSH_PROVIDER must not be LOG when push is enabled in prod.");
            } else if ("EXPO".equalsIgnoreCase(pushProvider)) {
                requireSecret(errors, "GRUN_PUSH_EXPO_URL", "grun.push.expo.url");
            } else if ("FCM".equalsIgnoreCase(pushProvider)) {
                requireSecret(errors, "GRUN_PUSH_FCM_PROJECT_ID", "grun.push.fcm.project-id");
                requireSecret(errors, "GRUN_PUSH_FCM_CREDENTIALS_JSON", "grun.push.fcm.credentials-json");
            } else if ("ONESIGNAL".equalsIgnoreCase(pushProvider)) {
                requireSecret(errors, "GRUN_PUSH_ONESIGNAL_APP_ID", "grun.push.onesignal.app-id");
                requireSecret(errors, "GRUN_PUSH_ONESIGNAL_API_KEY", "grun.push.onesignal.api-key");
            } else {
                errors.add("GRUN_PUSH_PROVIDER is unsupported.");
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid production configuration: " + String.join(" ", errors));
        }
    }

    private void requireSecret(List<String> errors, String envName, String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            errors.add(envName + " is required.");
        }
    }

    private void rejectValue(List<String> errors, String envName, String propertyName, String rejectedValue) {
        String value = environment.getProperty(propertyName);
        if (rejectedValue.equals(value)) {
            errors.add(envName + " must not use the local development fallback value.");
        }
    }
}
