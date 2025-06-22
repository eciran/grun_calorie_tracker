package com.grun.calorietracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GoalCalculationResponse {
    private int calculatedCalorieNeed;
    private int recommendedProteinGrams;
    private int recommendedFatGrams;
    private int recommendedCarbGrams;
}
