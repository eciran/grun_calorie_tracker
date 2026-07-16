package com.grun.calorietracker.dto;

import lombok.Data;

@Data
public class AiPreparationGuideSubstitutionDto {
    private String originalIngredient;
    private String substitute;
    private Boolean changesPlannedNutrition;
    private String nutritionImpactWarning;
}
