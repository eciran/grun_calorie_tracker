package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

// DTO for daily food log statistics
@Data
@Schema(description = "Daily aggregated nutrition totals.")
public class FoodLogDailyStatsDto {
    @Schema(description = "Aggregation date.", example = "2026-05-11")
    private String date;

    @Schema(description = "Total calories for the day.", example = "2140.0")
    private Double totalCalories;

    @Schema(description = "Total protein grams for the day.", example = "136.5")
    private Double totalProtein;

    @Schema(description = "Total carbohydrate grams for the day.", example = "220.0")
    private Double totalCarbs;

    @Schema(description = "Total fat grams for the day.", example = "72.0")
    private Double totalFat;

    @Schema(description = "Total fiber grams for the day, or null when unavailable.", example = "24.5")
    private Double totalFiber;

    @Schema(description = "Total sugar grams for the day, or null when unavailable.", example = "42.0")
    private Double totalSugar;

    @Schema(description = "Total saturated fat grams for the day, or null when unavailable.", example = "18.0")
    private Double totalSaturatedFat;

    @Schema(description = "Total sodium mg for the day, or null when unavailable.", example = "1840.0")
    private Double totalSodium;

    @Schema(description = "Total potassium mg for the day, or null when unavailable.", example = "2400.0")
    private Double totalPotassium;

    @Schema(description = "Total cholesterol mg for the day, or null when unavailable.", example = "160.0")
    private Double totalCholesterol;

    @Schema(description = "Total calcium mg for the day, or null when unavailable.", example = "780.0")
    private Double totalCalcium;

    @Schema(description = "Total iron mg for the day, or null when unavailable.", example = "10.5")
    private Double totalIron;

    @Schema(description = "Total magnesium mg for the day, or null when unavailable.", example = "260.0")
    private Double totalMagnesium;

    @Schema(description = "Total zinc mg for the day, or null when unavailable.", example = "8.5")
    private Double totalZinc;

    @Schema(description = "Total vitamin A micrograms for the day, or null when unavailable.", example = "700.0")
    private Double totalVitaminA;

    @Schema(description = "Total vitamin C mg for the day, or null when unavailable.", example = "65.0")
    private Double totalVitaminC;

    @Schema(description = "Total vitamin D micrograms for the day, or null when unavailable.", example = "8.0")
    private Double totalVitaminD;

    @Schema(description = "Total vitamin E mg for the day, or null when unavailable.", example = "12.0")
    private Double totalVitaminE;

    @Schema(description = "Total vitamin B12 micrograms for the day, or null when unavailable.", example = "2.4")
    private Double totalVitaminB12;
}
