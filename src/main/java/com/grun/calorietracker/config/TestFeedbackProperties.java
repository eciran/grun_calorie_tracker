package com.grun.calorietracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@ConfigurationProperties(prefix = "grun.test-feedback")
public class TestFeedbackProperties {

    private boolean enabled;
    private Set<String> allowedEnvironments = new LinkedHashSet<>(Set.of("preview", "test", "internal"));
    private int maxSubmissionsPerMinute = 6;
    private int maxDescriptionLength = 2000;
    private int idempotencyRetentionDays = 30;
    private java.time.Duration screenshotUploadUrlTtl = java.time.Duration.ofMinutes(10);
    private java.time.Duration screenshotAdminReadUrlTtl = java.time.Duration.ofMinutes(5);
    private java.time.Duration screenshotPendingTtl = java.time.Duration.ofHours(1);
    private int screenshotRetentionDays = 28;

    public boolean isEnabledFor(String environment) {
        if (!enabled || environment == null || environment.isBlank()) {
            return false;
        }
        String normalized = environment.trim().toLowerCase(Locale.ROOT);
        return allowedEnvironments.stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Set<String> getAllowedEnvironments() { return allowedEnvironments; }
    public void setAllowedEnvironments(Set<String> allowedEnvironments) { this.allowedEnvironments = allowedEnvironments; }
    public int getMaxSubmissionsPerMinute() { return maxSubmissionsPerMinute; }
    public void setMaxSubmissionsPerMinute(int value) { this.maxSubmissionsPerMinute = value; }
    public int getMaxDescriptionLength() { return maxDescriptionLength; }
    public void setMaxDescriptionLength(int value) { this.maxDescriptionLength = value; }
    public java.time.Duration getScreenshotUploadUrlTtl() { return screenshotUploadUrlTtl; }
    public void setScreenshotUploadUrlTtl(java.time.Duration value) { screenshotUploadUrlTtl = value; }
    public java.time.Duration getScreenshotAdminReadUrlTtl() { return screenshotAdminReadUrlTtl; }
    public void setScreenshotAdminReadUrlTtl(java.time.Duration value) { screenshotAdminReadUrlTtl = value; }
    public java.time.Duration getScreenshotPendingTtl() { return screenshotPendingTtl; }
    public void setScreenshotPendingTtl(java.time.Duration value) { screenshotPendingTtl = value; }
    public int getScreenshotRetentionDays() { return screenshotRetentionDays; }
    public void setScreenshotRetentionDays(int value) { screenshotRetentionDays = value; }
    public int getIdempotencyRetentionDays() { return idempotencyRetentionDays; }
    public void setIdempotencyRetentionDays(int value) { this.idempotencyRetentionDays = value; }
}
