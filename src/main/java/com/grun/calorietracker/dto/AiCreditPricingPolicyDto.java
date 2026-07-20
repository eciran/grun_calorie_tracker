package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AiCreditPricingMode;
import com.grun.calorietracker.enums.SubscriptionFeature;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiCreditPricingPolicyDto {
    private SubscriptionFeature feature;
    private AiCreditPricingMode pricingMode;
    private Integer baseCreditCost;
    private Integer includedUnits;
    private Integer unitsPerAdditionalCredit;
    private Integer contextSurcharge;
    private Integer maxCreditCost;
    private LocalDateTime updatedAt;
}
