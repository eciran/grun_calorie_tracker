package com.grun.calorietracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.grun.calorietracker.enums.SubscriptionPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Resolved feature access for the authenticated user's current subscription.")
public class SubscriptionFeatureAccessDto {
    private SubscriptionPlan planType;
    private Boolean activeEntitlement;
    private Boolean aiWorkoutPlanner;
    private Boolean aiRecipeGeneration;
    private Boolean healthIntegration;
    private Boolean advancedAnalytics;
    private Boolean adFree;
    private Boolean customFoodLibrary;
    private Integer aiMonthlyQuota;
    private Integer aiAddonQuota;
    private Integer aiRemainingThisPeriod;

    @JsonProperty("plan")
    @Schema(description = "Mobile-friendly alias for planType.", example = "PRO")
    public SubscriptionPlan getPlan() {
        return planType;
    }
}
