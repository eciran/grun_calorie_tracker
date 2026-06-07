package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI recipe ingredient suggestion with optional backend catalog match.")
public class AiRecipeIngredientSuggestionDto {
    @Schema(description = "Ingredient name suggested by AI.", example = "Chicken breast")
    private String name;

    @Schema(description = "Suggested amount.", example = "150.0")
    private Double portionSize;

    @Schema(description = "Suggested unit.", example = "GRAM")
    private FoodPortionUnit portionUnit;

    @Schema(description = "Matched food item id from the local catalog when backend can resolve it.", example = "12")
    private Long matchedFoodItemId;

    @Schema(description = "Whether user review is required before this ingredient can be used.", example = "true")
    private Boolean reviewRequired;

    @Schema(description = "Why this ingredient needs review or how it was matched.", example = "NO_CATALOG_MATCH")
    private String matchReason;

    @Schema(description = "Provider confidence from 0 to 1 when available.", example = "0.82")
    private Double confidence;
}
