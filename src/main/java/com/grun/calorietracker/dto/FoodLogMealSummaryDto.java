package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Food diary logs and nutrition totals grouped by meal for one day.")
public class FoodLogMealSummaryDto {

    @Schema(description = "Meal category.", example = "BREAKFAST")
    private String mealType;

    @Schema(description = "Calories consumed in this meal.", example = "420.5")
    private Double totalCalories;

    @Schema(description = "Protein grams consumed in this meal.", example = "28.0")
    private Double totalProtein;

    @Schema(description = "Fat grams consumed in this meal.", example = "15.5")
    private Double totalFat;

    @Schema(description = "Carbohydrate grams consumed in this meal.", example = "48.0")
    private Double totalCarbs;

    @Schema(description = "Food logs in this meal.")
    private List<FoodLogsDto> logs;
}
