package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Immutable nutrition estimate stored with an AI meal-plan item.")
public class MealPlanNutritionSnapshotDto {
    private Double calories;
    private Double protein;
    private Double carbs;
    private Double fat;
    private Double fiber;
    private Double sugar;
    private Double saturatedFat;
    private Double sodium;
    private Double potassium;
    private Double cholesterol;
    private Double calcium;
    private Double iron;
    private Double magnesium;
    private Double zinc;
    private Double vitaminA;
    private Double vitaminC;
    private Double vitaminD;
    private Double vitaminE;
    private Double vitaminB12;
}
