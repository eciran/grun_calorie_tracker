package com.grun.calorietracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiNutritionPlanMealDto {
    @NotBlank
    @Pattern(regexp = "(?i)BREAKFAST|LUNCH|DINNER|SNACK")
    private String mealType;

    private LocalTime suggestedTime;

    @Size(max = 500)
    private String summary;

    @NotEmpty
    @Size(max = 12)
    @Valid
    private List<AiNutritionPlanItemDto> items = new ArrayList<>();

    @Valid
    private MealPlanNutritionSnapshotDto totalNutrition;
}