package com.grun.calorietracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionFeature;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

@Data
@Schema(description = "Resolved feature access for the authenticated user's current subscription.")
public class SubscriptionFeatureAccessDto {
    private SubscriptionPlan planType;
    private Boolean activeEntitlement;
    private Boolean barcodeScanner;
    private Boolean manualFoodLogging;
    private Boolean foodDiary;
    private Boolean weightProgress;
    private Boolean waterTracking;
    private Boolean workoutLogging;
    private Boolean savedMealTemplates;
    private Boolean recipeBuilder;
    private Boolean publicRecipeLibrary;
    private Boolean advancedMacroTargets;
    private Boolean micronutrientDetails;
    private Boolean dataExport;
    private Boolean fastingBasic;
    private Boolean fastingAdvanced;
    private Boolean aiMealDrafts;
    private Integer aiMealDraftsCreditCost;
    private Boolean aiWorkoutPlanner;
    private Integer aiWorkoutPlannerCreditCost;
    private Boolean aiRecipeGeneration;
    private Integer aiRecipeGenerationCreditCost;
    private Boolean aiMealPreparationGuide;
    private Integer aiMealPreparationGuideCreditCost;
    private Boolean aiNutritionPlan;
    private Integer aiNutritionPlanBaseCreditCost;
    private Boolean aiInsights;
    private Integer aiInsightsCreditCost;
    private Map<SubscriptionFeature, Integer> aiCreditCosts;
    private Boolean healthIntegration;
    private Boolean advancedAnalytics;
    private Boolean adFree;
    private Boolean customFoodLibrary;
    private Integer aiMonthlyQuota;
    private Integer aiAddonQuota;
    private Integer aiUsedThisPeriod;
    private Integer aiBaseRemainingThisPeriod;
    private Integer aiAddonRemainingThisPeriod;
    private LocalDate aiAddonQuotaExpiresAt;
    private Integer aiRemainingThisPeriod;

    @JsonProperty("plan")
    @Schema(description = "Mobile-friendly alias for planType.", example = "PRO")
    public SubscriptionPlan getPlan() {
        return planType;
    }
}
