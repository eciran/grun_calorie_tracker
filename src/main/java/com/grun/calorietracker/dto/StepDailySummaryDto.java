package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.HealthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "Daily step tracking summary.")
public class StepDailySummaryDto {
    private LocalDate date;
    private Integer totalSteps;
    private Integer targetSteps;
    private Integer remainingSteps;
    private Double progressPercent;
    private Boolean targetReached;
    private Double totalDistanceMeters;
    private Double totalCaloriesBurned;
    private Integer currentStreakDays;
    private Boolean hasStepData;
    private LocalDateTime latestStepAt;
    private List<HealthProvider> providers;
}
