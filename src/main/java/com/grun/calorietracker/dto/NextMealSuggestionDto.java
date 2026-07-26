package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.NextMealStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "Deterministic next-meal target and verified catalog suggestions for the home screen.")
public class NextMealSuggestionDto {
    private NextMealStatus status;
    private LocalDate targetDate;
    private LocalDateTime generatedAt;
    private String mealType;
    private Double targetCalories;
    private Double targetProtein;
    private Double targetCarbs;
    private Double targetFat;
    private Double dailyRemainingCalories;
    private Double dailyRemainingProtein;
    private Double dailyRemainingCarbs;
    private Double dailyRemainingFat;
    private List<NextMealRecipeSuggestionDto> recipeSuggestions = new ArrayList<>();
    private Boolean aiRecipeAvailable;
    private Integer aiRecipeCreditCost;
    private NextMealAiRecipePrefillDto aiRecipePrefill;
}
