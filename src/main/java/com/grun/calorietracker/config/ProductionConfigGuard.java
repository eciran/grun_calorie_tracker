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
            requirePositive(errors, "GRUN_BREVO_TEMPLATE_EMAIL_VERIFICATION_EN", "grun.mail.brevo.templates.email-verification-en");
            requirePositive(errors, "GRUN_BREVO_TEMPLATE_EMAIL_VERIFICATION_TR", "grun.mail.brevo.templates.email-verification-tr");
            requirePositive(errors, "GRUN_BREVO_TEMPLATE_PASSWORD_RESET_EN", "grun.mail.brevo.templates.password-reset-en");
            requirePositive(errors, "GRUN_BREVO_TEMPLATE_PASSWORD_RESET_TR", "grun.mail.brevo.templates.password-reset-tr");
            requirePositive(errors, "GRUN_BREVO_TEMPLATE_SUBSCRIPTION_FEATURE_CHANGE_EN", "grun.mail.brevo.templates.subscription-feature-change-en");
            requirePositive(errors, "GRUN_BREVO_TEMPLATE_SUBSCRIPTION_FEATURE_CHANGE_TR", "grun.mail.brevo.templates.subscription-feature-change-tr");
            requirePositive(errors, "GRUN_BREVO_TEMPLATE_ADMIN_INVITATION_EN", "grun.mail.brevo.templates.admin-invitation-en");
            requirePositive(errors, "GRUN_BREVO_TEMPLATE_ADMIN_INVITATION_TR", "grun.mail.brevo.templates.admin-invitation-tr");
            requirePositive(errors, "GRUN_BREVO_TEMPLATE_ADMIN_PASSWORD_CHANGED_EN", "grun.mail.brevo.templates.admin-password-changed-en");
            requirePositive(errors, "GRUN_BREVO_TEMPLATE_ADMIN_PASSWORD_CHANGED_TR", "grun.mail.brevo.templates.admin-password-changed-tr");
        }

        boolean pushEnabled = environment.getProperty("grun.push.enabled", Boolean.class, false);
        String pushProvider = environment.getProperty("grun.push.provider", "LOG");
        if (!pushEnabled) {
            errors.add("GRUN_PUSH_ENABLED must be true in prod.");
        } else if (!"EXPO".equalsIgnoreCase(pushProvider)) {
            errors.add("GRUN_PUSH_PROVIDER must be EXPO because the mobile app registers Expo push tokens.");
        } else {
            requireSecret(errors, "GRUN_PUSH_EXPO_URL", "grun.push.expo.url");
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

    private void requirePositive(List<String> errors, String envName, String propertyName) {
        Long value = environment.getProperty(propertyName, Long.class);
        if (value == null || value <= 0) {
            errors.add(envName + " must be a positive integer.");
        }
    }
}
