package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AdminRecipeImportIngredientUpdateRequestDto {
    @NotNull
    @Positive
    private Long foodItemId;
}
