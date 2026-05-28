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

    @Schema(description = "Current server timestamp.")
    private LocalDateTime checkedAt;
}
