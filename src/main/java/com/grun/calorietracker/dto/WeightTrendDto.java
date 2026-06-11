package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Smoothed weight trend summary based on recent progress logs.")
public class WeightTrendDto {
    private Double currentSevenDayAverageKg;
    private Double previousSevenDayAverageKg;
    private Double weeklyChangeKg;
    private String trendDirection;
    private LocalDate projectedGoalDate;
}
