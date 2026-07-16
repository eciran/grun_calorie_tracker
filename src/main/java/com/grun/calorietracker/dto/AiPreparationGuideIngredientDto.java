package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import lombok.Data;

@Data
public class AiPreparationGuideIngredientDto {
    private String name;
    private Double quantity;
    private FoodPortionUnit unit;
    private Boolean optional;
    private Boolean changesPlannedNutrition;
}
