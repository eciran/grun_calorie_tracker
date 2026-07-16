package com.grun.calorietracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.FoodPortionUnit;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiPreparationGuideResponseDto implements AiUsageMetadataCarrier {
    private Long guideId;
    private Long requestId;
    private String schemaVersion = "ai_preparation_guide_v1";
    private AiRequestType requestType;
    private AiRequestStatus status;
    private AiProvider provider;
    private String model;
    private Long mealPlanId;
    private Long mealPlanItemId;
    private Integer version;
    private String itemName;
    private Double plannedQuantity;
    private FoodPortionUnit plannedUnit;
    private MealPlanNutritionSnapshotDto plannedNutrition;
    private Integer preparationMinutes;
    private Integer cookingMinutes;
    private List<String> equipment = new ArrayList<>();
    private List<AiPreparationGuideIngredientDto> ingredients = new ArrayList<>();
    private List<AiPreparationGuideStepDto> steps = new ArrayList<>();
    private List<String> foodSafetyNotes = new ArrayList<>();
    private List<String> storageInstructions = new ArrayList<>();
    private List<AiPreparationGuideSubstitutionDto> substitutions = new ArrayList<>();
    private List<String> nutritionImpactWarnings = new ArrayList<>();
    private List<String> assumptions = new ArrayList<>();
    private Boolean reviewRequired = true;
    private Integer qualityScore;
    private Double confidence;
    private String estimatedUncertainty;
    private Integer creditCost;
    private Integer aiRemainingThisPeriod;
    private LocalDateTime createdAt;
    @JsonIgnore private Integer promptTokens;
    @JsonIgnore private Integer completionTokens;
    @JsonIgnore private Integer totalTokens;
    @JsonIgnore private Double estimatedCost;
    @JsonIgnore private String costCurrency;
}
