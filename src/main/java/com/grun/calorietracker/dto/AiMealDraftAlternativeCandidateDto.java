package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Alternative AI food identity with a complete nutrition snapshot for the same visible portion.")
public class AiMealDraftAlternativeCandidateDto {
    @Schema(description = "Alternative food identity.", example = "Popcorn Chicken")
    private String name;

    @Schema(description = "Estimated quantity for this alternative.", example = "10")
    private Double quantity;

    @Schema(description = "Estimated portion unit.", example = "PIECE")
    private String unit;

    @Schema(description = "Number of reliably visible pieces when the food is countable.", example = "10")
    private Integer detectedPieceCount;

    @Schema(description = "Estimated total weight for the alternative's complete visible portion.", example = "220")
    private Double estimatedTotalWeightGrams;

    @Schema(description = "Complete macro and micronutrient snapshot for the alternative's estimated portion.")
    private RecipeNutritionDto estimatedNutrition;

    @Schema(description = "Nutrition uncertainty note specific to this alternative.")
    private String nutritionEstimateNote;

    @Schema(description = "Why this alternative is visually plausible.")
    private String matchReason;

    @Schema(description = "Confidence between 0 and 1.", example = "0.68")
    private Double confidence;

    @Schema(description = "Whether selecting this alternative materially changes nutrition.", example = "true")
    private Boolean materiallyDifferent;
}
