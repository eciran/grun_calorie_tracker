package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.NutritionPlanGenerationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Backend-calculated AI credit cost for a nutrition-plan request.")
public class AiNutritionPlanCreditEstimateDto {
    private Integer dayCount;
    private Integer mealsPerDay;
    private NutritionPlanGenerationMode generationMode;
    private Integer baseCreditCost;
    private Integer totalMealSlots;
    private Integer includedMealSlots;
    private Integer mealSlotsPerAdditionalCredit;
    private Integer additionalCredits;
    private Boolean workoutContextIncluded;
    private Integer workoutContextCreditCost;
    private Integer totalCreditCost;
}