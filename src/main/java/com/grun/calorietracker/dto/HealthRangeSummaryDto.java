package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "Health summary for a date range.")
public class HealthRangeSummaryDto {

    @Schema(description = "Range start date.", example = "2026-05-20")
    private LocalDate startDate;

    @Schema(description = "Range end date.", example = "2026-05-26")
    private LocalDate endDate;

    @Schema(description = "Total synced steps in the range.", example = "42000")
    private Integer totalSteps;

    @Schema(description = "Total active calories burned in the range.", example = "2100.0")
    private Double totalCaloriesBurned;

    @Schema(description = "Total distance in meters in the range.", example = "32000.0")
    private Double totalDistanceMeters;

    @Schema(description = "Total sleep hours in the range.", example = "49.0")
    private Double totalSleepHours;

    @Schema(description = "Average heart rate across the range.", example = "73.2")
    private Double averageHeartRate;

    @Schema(description = "Whether at least one health metric exists in the range.", example = "true")
    private Boolean hasHealthData;

    @Schema(description = "Daily summaries in ascending date order.")
    private List<HealthDailySummaryDto> days;
}
