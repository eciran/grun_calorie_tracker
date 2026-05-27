package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.HealthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "Daily health data summary aggregated from connected third-party providers.")
public class HealthDailySummaryDto {

    @Schema(description = "Summary date.", example = "2026-05-26")
    private LocalDate summaryDate;

    @Schema(description = "Providers connected by the user.")
    private List<HealthProvider> connectedProviders;

    @Schema(description = "Total synced steps for the day.", example = "8500")
    private Integer totalSteps;

    @Schema(description = "Total active calories burned from health providers.", example = "410.0")
    private Double totalCaloriesBurned;

    @Schema(description = "Total distance in meters from health providers.", example = "6200.0")
    private Double totalDistanceMeters;

    @Schema(description = "Total sleep hours from health providers.", example = "7.4")
    private Double totalSleepHours;

    @Schema(description = "Average heart rate from health samples.", example = "72.5")
    private Double averageHeartRate;

    @Schema(description = "Whether at least one health metric exists for the date.", example = "true")
    private Boolean hasHealthData;

    @Schema(description = "Latest health sync timestamp across connected providers.")
    private LocalDateTime latestSyncAt;
}
