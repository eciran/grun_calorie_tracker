package com.grun.calorietracker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "AI suggested food item draft. It is not written to the diary until the user confirms it.")
public class AiMealDraftItemDto {
    @Schema(description = "Food or meal item name suggested by AI.", example = "Grilled chicken breast")
    private String name;

    @Schema(description = "Quantity detected or estimated from voice/photo input.", example = "150")
    private Double quantity;

    @Schema(description = "Unit detected or estimated from input.", example = "g")
    private String unit;

    @Schema(description = "Number of visually identical pieces grouped into this item.", example = "2")
    private Integer detectedPieceCount;

    @Schema(description = "Estimated combined cooked weight of all grouped pieces in grams.", example = "360")
    private Double estimatedTotalWeightGrams;

    @Schema(description = "Estimated calories for this item.", example = "248")
    private Double estimatedCalories;

    @Schema(description = "Estimated protein grams.", example = "46.5")
    private Double estimatedProtein;

    @Schema(description = "Estimated carbohydrate grams.", example = "0")
    private Double estimatedCarbs;

    @Schema(description = "Estimated fat grams.", example = "5.4")
    private Double estimatedFat;

    @Schema(description = "Complete AI-estimated nutrition for this item's detected quantity. This is an editable estimate, not verified catalog data.")
    private RecipeNutritionDto estimatedNutrition;

    @Schema(description = "Plain-language explanation of nutrition assumptions and unavailable or uncertain micronutrients.")
    private String nutritionEstimateNote;

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

    @Schema(description = "How the portion was estimated.", example = "VISUAL_ESTIMATE")
    private String portionEstimateMethod;

    @Schema(description = "User-facing explanation of why this item was identified and how the estimate was produced.")
    private String reasoning;

    @Schema(description = "Short portion note shown to the user, especially when grams/ml/serving were estimated.")
    private String portionNote;

    @Schema(description = "Whether the item was visibly detected in the photo input.", example = "true")
    private Boolean visibleInPhoto;

    @Schema(description = "Whether the user should confirm grams/ml/serving before logging.", example = "true")
    private Boolean needsUserPortionConfirmation;

    @Schema(description = "Alternative catalog/product names that may match this item.")
    private List<String> alternativeMatchNames = new ArrayList<>();

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Optional nutrition-complete alternatives for the same visible portion. Present only in the v4 experiment.")
    private List<AiMealDraftAlternativeCandidateDto> alternativeCandidates = new ArrayList<>();
}