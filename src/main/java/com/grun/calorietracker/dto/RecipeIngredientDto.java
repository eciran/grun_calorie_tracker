package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Ingredient response item for a recipe.")
public class RecipeIngredientDto {
    private Long foodItemId;
    private Long servingOptionId;
    private String servingOptionLabel;
    private String foodName;
    private Boolean snapshotIngredient;
    private Double portionSize;
    private FoodPortionUnit portionUnit;
    private Double normalizedPortionGrams;
    private Double normalizedPortionMilliliters;
    private Double snapshotCalories;
    private Double snapshotProtein;
    private Double snapshotCarbs;
    private Double snapshotFat;
    private Double snapshotFiber;
    private Double snapshotSugar;
    private Double snapshotSaturatedFat;
    private Double snapshotSodium;
    private Double snapshotPotassium;
    private Double snapshotCholesterol;
    private Double snapshotCalcium;
    private Double snapshotIron;
    private Double snapshotMagnesium;
    private Double snapshotZinc;
    private Double snapshotVitaminA;
    private Double snapshotVitaminC;
    private Double snapshotVitaminD;
    private Double snapshotVitaminE;
    private Double snapshotVitaminB12;
}
