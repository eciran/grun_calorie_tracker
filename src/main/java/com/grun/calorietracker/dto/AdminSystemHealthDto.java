package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin-only production health summary without secrets or infrastructure credentials.")
public class AdminSystemHealthDto {

    @Schema(description = "Overall application status.", example = "UP")
    private String status;

    @Schema(description = "Application name.", example = "grun-calorie-tracker")
    private String appName;

    @Schema(description = "Application version.", example = "0.0.1-SNAPSHOT")
    private String appVersion;

    @Schema(description = "Active Spring profiles.", example = "[\"prod\"]")
    private List<String> activeProfiles;

    @Schema(description = "Database connectivity status.", example = "UP")
    private String databaseStatus;

    @Schema(description = "Database connection validation latency in milliseconds.", example = "12")
    private Long databaseLatencyMs;

    @Schema(description = "JVM uptime in milliseconds.", example = "3600000")
    private Long uptimeMs;

    @Schema(description = "Available JVM processors.", example = "2")
    private Integer availableProcessors;

    @Schema(description = "Current heap memory usage in MB.", example = "128")
    private Long heapUsedMb;

    @Schema(description = "Maximum heap memory in MB.", example = "512")
    private Long heapMaxMb;

    @Schema(description = "RevenueCat provider events received in the last 24 hours.", example = "42")
    private Long revenueCatEventsLast24h;

    @Schema(description = "RevenueCat provider events currently failed and requiring admin review.", example = "1")
    private Long failedRevenueCatEvents;

    @Schema(description = "Active or trialing subscriptions.", example = "123")
    private Long activeSubscriptions;

    @Schema(description = "Active subscriptions whose AI quota is exhausted.", example = "8")
    private Long exhaustedAiQuotaSubscriptions;

    @Schema(description = "System alert notifications created in the last 24 hours, including mail provider failures.", example = "0")
    private Long systemAlertsLast24h;

    @Schema(description = "Whether AI endpoints are enabled by backend configuration.", example = "false")
    private Boolean aiEnabled;

    @Schema(description = "Configured AI provider name without credentials.", example = "DISABLED")
    private String aiProvider;

    @Schema(description = "Configured AI model name without credentials.", example = "not-configured")
    private String aiModel;

    @Schema(description = "AI draft requests created in the last 24 hours.", example = "120")
    private Long aiRequestsLast24h;

    @Schema(description = "Failed AI draft requests created in the last 24 hours.", example = "3")
    private Long failedAiRequestsLast24h;

    @Schema(description = "AI request failure rate in the last 24 hours, between 0 and 1.", example = "0.025")
    private Double aiFailureRateLast24h;

    @Schema(description = "AI meal drafts created successfully in the last 7 days.", example = "250")
    private Long aiDraftsLast7d;

    @Schema(description = "AI meal drafts confirmed by users in the last 7 days.", example = "180")
    private Long confirmedAiDraftsLast7d;

    @Schema(description = "AI meal drafts rejected by users in the last 7 days.", example = "20")
    private Long rejectedAiDraftsLast7d;

    @Schema(description = "AI rejected draft reason distribution in the last 7 days.")
    private Map<String, Long> aiRejectionReasonsLast7d;

    @Schema(description = "AI meal drafts still open in the last 7 days.", example = "50")
    private Long openAiDraftsLast7d;

    @Schema(description = "AI draft confirmation rate in the last 7 days, between 0 and 1.", example = "0.72")
    private Double aiDraftConfirmationRateLast7d;

    @Schema(description = "Food log completion events recorded in the last 24 hours.", example = "320")
    private Long logFlowCompletedLast24h;

    @Schema(description = "Average measured food logging duration in milliseconds in the last 24 hours.", example = "8700")
    private Long averageLogFlowDurationMsLast24h;

    @Schema(description = "Quick-log suggestion apply events recorded in the last 24 hours.", example = "180")
    private Long quickLogSuggestionAppliedLast24h;

    @Schema(description = "Product search start events recorded in the last 24 hours.", example = "540")
    private Long searchStartedLast24h;

    @Schema(description = "Operational warnings derived from current runtime and integration counters.")
    private List<String> warnings;

    @Schema(description = "Current server timestamp.")
    private LocalDateTime checkedAt;
}
