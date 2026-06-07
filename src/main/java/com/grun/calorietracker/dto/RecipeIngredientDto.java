package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Ingredient response item for a recipe.")
public class RecipeIngredientDto {
    private Long foodItemId;
    private String foodName;
    private Double portionSize;
    private FoodPortionUnit portionUnit;
    private Double normalizedPortionGrams;
}
