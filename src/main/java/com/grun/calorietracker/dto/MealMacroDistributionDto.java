package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Macro and calorie distribution for one meal bucket.")
public class MealMacroDistributionDto {
    private String mealType;
    private Double calories;
    private Double protein;
    private Double carbs;
    private Double fat;
    private Double calorieSharePercent;
}
