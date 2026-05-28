package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.SubscriptionPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "Admin-managed feature entitlement rule for a subscription plan.")
public class SubscriptionPlanFeatureDto {
    private SubscriptionPlan planType;
    private SubscriptionFeature feature;
    private Boolean enabled;
    private LocalDate effectiveFrom;
    private LocalDateTime updatedAt;
}
