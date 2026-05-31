package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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

    @Schema(description = "Operational warnings derived from current runtime and integration counters.")
    private List<String> warnings;

    @Schema(description = "Current server timestamp.")
    private LocalDateTime checkedAt;
}
