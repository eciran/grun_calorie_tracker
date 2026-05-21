package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
@Schema(description = "User-owned manual food product definition.")
public class CustomFoodRequestDto {

    @NotBlank(message = "{validation.custom-food.name.required}")
    @Schema(description = "Custom food name.", example = "Homemade lentil soup", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull(message = "{validation.custom-food.calories.required}")
    @PositiveOrZero(message = "{validation.custom-food.nutrition.non-negative}")
    @Schema(description = "Calories per 100 grams.", example = "92.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double calories;

    @PositiveOrZero(message = "{validation.custom-food.nutrition.non-negative}")
    @Schema(description = "Protein grams per 100 grams.", example = "5.8")
    private Double protein;

    @PositiveOrZero(message = "{validation.custom-food.nutrition.non-negative}")
    @Schema(description = "Fat grams per 100 grams.", example = "2.4")
    private Double fat;

    @PositiveOrZero(message = "{validation.custom-food.nutrition.non-negative}")
    @Schema(description = "Carbohydrate grams per 100 grams.", example = "12.1")
    private Double carbs;

    @PositiveOrZero(message = "{validation.custom-food.nutrition.non-negative}")
    @Schema(description = "Optional default serving size in grams.", example = "250.0")
    private Double servingSizeGrams;

    @Schema(description = "Optional serving label.", example = "bowl")
    private String servingUnit;
}
