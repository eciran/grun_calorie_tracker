package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Ingredient request item for a recipe. Send either foodItemId for a catalog ingredient or snapshotFoodName plus snapshot nutrition for AI/manual estimate ingredients.")
public class RecipeIngredientRequestDto {
    @Positive
    @Schema(description = "Optional catalog food product id. Omit for AI/manual snapshot ingredients.", example = "12")
    private Long foodItemId;

    @Size(max = 220)
    @Schema(description = "Ingredient display name used when no catalog foodItemId is selected.", example = "Lean Steak")
    private String snapshotFoodName;

    @Positive
    @Schema(description = "Ingredient amount in the selected unit.", example = "150.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double portionSize;

    @Schema(description = "Ingredient unit. Defaults to GRAM when omitted. TABLESPOON normalizes to 15g/ml, TEASPOON normalizes to 5g/ml, and SLICE uses product servingSizeGrams like PIECE when catalog data is available.", example = "TABLESPOON", allowableValues = {"GRAM", "MILLILITER", "TABLESPOON", "TEASPOON", "SLICE", "SERVING", "PIECE"})
    private FoodPortionUnit portionUnit;

    @Schema(description = "Snapshot calories per 100g/ml used when no catalog foodItemId is selected.", example = "190.0")
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