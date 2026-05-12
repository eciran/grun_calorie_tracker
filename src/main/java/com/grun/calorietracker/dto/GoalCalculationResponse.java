package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Calculated daily calorie and macro recommendations.")
public class GoalCalculationResponse {
    @Schema(description = "Calculated daily calorie need.", example = "2300")
    private int calculatedCalorieNeed;

    @Schema(description = "Recommended daily protein amount in grams.", example = "160")
    private int recommendedProteinGrams;

    @Schema(description = "Recommended daily fat amount in grams.", example = "70")
    private int recommendedFatGrams;

    @Schema(description = "Recommended daily carbohydrate amount in grams.", example = "250")
    private int recommendedCarbGrams;
}
