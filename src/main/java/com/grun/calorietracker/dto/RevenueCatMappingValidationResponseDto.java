package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.SubscriptionPlan;
import lombok.Data;

@Data
public class RevenueCatMappingValidationResponseDto {
    private boolean recognized;
    private String mappingType;
    private SubscriptionPlan subscriptionPlan;
    private Integer aiAddonQuota;
    private Integer aiAddonValidityDays;
    private boolean strictProductMapping;
    private String message;
}
