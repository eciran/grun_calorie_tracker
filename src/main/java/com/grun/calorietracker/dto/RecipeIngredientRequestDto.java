package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "Ingredient request item for a recipe.")
public class RecipeIngredientRequestDto {
    @NotNull
    @Positive
    @Schema(description = "Food product id used as ingredient.", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long foodItemId;

    @NotNull
    @Positive
    @Schema(description = "Ingredient amount in the selected unit.", example = "150.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double portionSize;

    @Schema(description = "Ingredient unit. Defaults to GRAM when omitted.", example = "GRAM")
    private FoodPortionUnit portionUnit;
}
