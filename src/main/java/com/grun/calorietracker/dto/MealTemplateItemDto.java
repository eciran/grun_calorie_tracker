package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "One food item stored in a saved meal template.")
public class MealTemplateItemDto {

    private Long foodItemId;
    private String foodName;
    private Double portionSize;
    private FoodPortionUnit portionUnit;
    private Double normalizedPortionGrams;

    @Schema(description = "Calories for this template item using its stored portion.", example = "180.0")
    private Double calories;

    @Schema(description = "Protein in grams for this template item using its stored portion.", example = "12.0")
    private Double protein;

    @Schema(description = "Carbohydrates in grams for this template item using its stored portion.", example = "22.0")
    private Double carbs;

    @Schema(description = "Fat in grams for this template item using its stored portion.", example = "6.0")
    private Double fat;
}