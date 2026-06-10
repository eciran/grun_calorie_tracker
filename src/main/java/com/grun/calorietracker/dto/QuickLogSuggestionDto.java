package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "Aggregated quick-log suggestions for the first food logging screen.")
public class QuickLogSuggestionDto {

    @Schema(description = "Target diary date.", example = "2026-06-10")
    private LocalDate targetDate;

    @Schema(description = "Backend suggested meal type based on request time.", example = "BREAKFAST")
    private String suggestedMealType;

    @Schema(description = "Recent logged meal occurrences that can be copied.")
    private List<FoodLogRecentMealDto> recentMeals;

    @Schema(description = "Saved meal templates filtered for the suggested meal type first.")
    private List<MealTemplateDto> mealTemplates;

    @Schema(description = "Recently logged individual products.")
    private List<FoodProductDto> recentProducts;

    @Schema(description = "Favorite individual products.")
    private List<FoodProductDto> favoriteProducts;
}
