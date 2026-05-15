package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Daily dashboard summary for calories, macros, exercise, weight, and active goal targets.")
public class DailySummaryDto {

    @Schema(description = "Summary date.", example = "2026-05-15")
    private LocalDate summaryDate;

    @Schema(description = "Target calories for the day.", example = "2400")
    private Integer targetCalories;
    @Schema(description = "Calories consumed from food logs.", example = "1350.5")
    private Double consumedCalories;
    @Schema(description = "Calories burned from exercise logs.", example = "420.0")
    private Double burnedCalories;
    @Schema(description = "Remaining calories after consumed and burned calories are applied.", example = "1469.5")
    private Double remainingCalories;

    @Schema(description = "Target protein grams.", example = "160.0")
    private Double targetProtein;
    @Schema(description = "Target fat grams.", example = "70.0")
    private Double targetFat;
    @Schema(description = "Target carbohydrate grams.", example = "260.0")
    private Double targetCarbs;

    @Schema(description = "Consumed protein grams.", example = "95.5")
    private Double consumedProtein;
    @Schema(description = "Consumed fat grams.", example = "45.0")
    private Double consumedFat;
    @Schema(description = "Consumed carbohydrate grams.", example = "140.0")
    private Double consumedCarbs;

    @Schema(description = "Current user weight from latest progress log or user profile.", example = "82.0")
    private Double currentWeight;
    @Schema(description = "Target weight from active user goal.", example = "78.0")
    private Double targetWeight;

    @Schema(description = "Active goal type.", example = "LOSE_WEIGHT")
    private String goalType;

    @Schema(description = "Total exercise duration in minutes for the day.", example = "45")
    private Integer totalExerciseMinutes;
}
