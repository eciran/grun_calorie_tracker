package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

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
    @Schema(description = "Net calories after exercise is subtracted from consumed calories.", example = "930.5")
    private Double netCalories;
    @Schema(description = "Consumed calories as percentage of target calories.", example = "56.27")
    private Double calorieProgressPercent;

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
    @Schema(description = "Remaining protein grams for the day.", example = "64.5")
    private Double remainingProtein;
    @Schema(description = "Remaining fat grams for the day.", example = "25.0")
    private Double remainingFat;
    @Schema(description = "Remaining carbohydrate grams for the day.", example = "120.0")
    private Double remainingCarbs;
    @Schema(description = "Consumed protein as percentage of target protein.", example = "59.69")
    private Double proteinProgressPercent;
    @Schema(description = "Consumed fat as percentage of target fat.", example = "64.29")
    private Double fatProgressPercent;
    @Schema(description = "Consumed carbohydrates as percentage of target carbohydrates.", example = "53.85")
    private Double carbsProgressPercent;

    @Schema(description = "Micronutrients consumed on the summary date. Null means no micronutrient data was available.")
    private MicronutrientTotalsDto consumedMicros;

    @Schema(description = "Current user weight from latest progress log or user profile.", example = "82.0")
    private Double currentWeight;
    @Schema(description = "Target weight from active user goal.", example = "78.0")
    private Double targetWeight;

    @Schema(description = "Active goal type.", example = "LOSE_WEIGHT")
    private String goalType;

    @Schema(description = "Total exercise duration in minutes for the day.", example = "45")
    private Integer totalExerciseMinutes;

    @Schema(description = "Whether the user has a saved active goal.", example = "true")
    private Boolean hasActiveGoal;
    @Schema(description = "Whether the user profile and goal setup is complete enough for the main tracking flow.", example = "true")
    private Boolean onboardingCompleted;

    @Schema(description = "Whether at least one food log exists on the summary date.", example = "true")
    private Boolean hasFoodLogs;

    @Schema(description = "Whether at least one exercise log exists on the summary date.", example = "false")
    private Boolean hasExerciseLogs;

    @Schema(description = "Whether any food or exercise diary entry exists on the summary date.", example = "true")
    private Boolean hasAnyDiaryEntry;

    @Schema(description = "Food diary entries logged on the summary date.")
    private List<FoodLogsDto> foodLogs;

    @Schema(description = "Exercise diary entries logged on the summary date.")
    private List<ExerciseLogsDto> exerciseLogs;

    @Schema(description = "Third-party health data summary for the same date.")
    private HealthDailySummaryDto healthSummary;
}
