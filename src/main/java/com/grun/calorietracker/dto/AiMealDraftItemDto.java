package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI suggested food item draft. It is not written to the diary until the user confirms it.")
public class AiMealDraftItemDto {
    @Schema(description = "Food or meal item name suggested by AI.", example = "Grilled chicken breast")
    private String name;

    @Schema(description = "Quantity detected or estimated from voice/photo input.", example = "150")
    private Double quantity;

    @Schema(description = "Unit detected or estimated from input.", example = "g")
    private String unit;

    @Schema(description = "Estimated calories for this item.", example = "248")
    private Double estimatedCalories;

    @Schema(description = "Estimated protein grams.", example = "46.5")
    private Double estimatedProtein;

    @Schema(description = "Estimated carbohydrate grams.", example = "0")
    private Double estimatedCarbs;

    @Schema(description = "Estimated fat grams.", example = "5.4")
    private Double estimatedFat;

    @Schema(description = "Optional matched product id when backend can map the suggestion to a catalog item.", example = "123")
    private Long matchedFoodItemId;

    @Schema(description = "Whether the user must manually review or match this item before confirmation.", example = "true")
    private Boolean reviewRequired;

    @Schema(description = "Short reason explaining why review is required or how the item was matched.", example = "LOW_CONFIDENCE")
    private String matchReason;

    @Schema(description = "Optional safety warning for unusual or health-sensitive AI estimates.", example = "EXTREME_CALORIE_ESTIMATE")
    private String safetyWarning;

    @Schema(description = "AI confidence between 0 and 1.", example = "0.62")
    private Double confidence;
}
