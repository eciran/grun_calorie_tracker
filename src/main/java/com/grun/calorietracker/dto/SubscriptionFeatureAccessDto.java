package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.SubscriptionPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Resolved feature access for the authenticated user's current subscription.")
public class SubscriptionFeatureAccessDto {
    private SubscriptionPlan planType;
    private Boolean activeEntitlement;
    private Boolean aiWorkoutPlanner;
    private Boolean healthIntegration;
    private Boolean advancedAnalytics;
    private Boolean adFree;
    private Boolean customFoodLibrary;
    private Integer aiMonthlyQuota;
    private Integer aiAddonQuota;
    private Integer aiRemainingThisPeriod;
}
