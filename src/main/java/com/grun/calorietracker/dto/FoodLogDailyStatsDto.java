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
}
