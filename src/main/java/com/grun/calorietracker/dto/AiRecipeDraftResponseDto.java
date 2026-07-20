package com.grun.calorietracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "AI-generated recipe draft. It is not persisted as a recipe until the user confirms the final recipe request.")
public class AiRecipeDraftResponseDto implements AiUsageMetadataCarrier {
    private Long requestId;
    private String schemaVersion = "ai_response_v3";
    private AiRequestType requestType;
    private AiRequestStatus status;
    private AiProvider provider;
    private String model;
    private String summary;
    @Schema(description = "Stable result type used by the mobile UI to present this as an AI-estimated recipe snapshot.", example = "AI_SNAPSHOT")
    private String resultType = "AI_SNAPSHOT";
    @Schema(description = "Short premium-facing explanation shown near the top of the AI recipe result.")
    private String userMessage;
    @Schema(description = "Polished professional summary explaining the recipe strategy, fit, and caveats.")
    private String professionalSummary;
    @Schema(description = "Plain-language assumptions behind the recipe and nutrition estimate.")
    private List<String> assumptions = new ArrayList<>();
    @Schema(description = "Concrete next actions before saving or cooking the recipe.")
    private List<String> nextBestActions = new ArrayList<>();
    private Boolean reviewRequired = true;
    private Double confidence;
    private Integer qualityScore;
    private String estimatedUncertainty;
    private List<String> reviewReasons = new ArrayList<>();
    private Integer aiRemainingThisPeriod;
    private RecipeRequestDto suggestedRecipe;
    private List<AiRecipeIngredientSuggestionDto> suggestedIngredients = new ArrayList<>();
    @Schema(description = "AI-estimated nutrition for the full draft recipe. This is preview data; persisted recipe nutrition is recalculated by backend from confirmed ingredients.")
    private RecipeNutritionDto estimatedNutritionTotal;
    @Schema(description = "AI-estimated nutrition for one default serving. This is preview data; persisted recipe nutrition is recalculated by backend from confirmed ingredients.")
    private RecipeNutritionDto estimatedNutritionPerServing;
    @Schema(description = "Human-readable note explaining nutrition assumptions or uncertainty.")
    private String nutritionEstimateNote;
    @Schema(description = "Cooking and preparation tips that make the generated recipe feel practical and premium.")
    private List<String> cookingTips = new ArrayList<>();
    @Schema(description = "Suggested substitutions for common diet, availability, or taste adjustments.")
    private List<String> substitutions = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    @JsonIgnore
    private Integer promptTokens;
    @JsonIgnore
    private Integer completionTokens;
    @JsonIgnore
    private Integer totalTokens;
    @JsonIgnore
    private Double estimatedCost;
    @JsonIgnore
    private String costCurrency;
}