package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.SubscriptionFeature;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiCreditCostEstimateDto {
    private SubscriptionFeature feature;
    private Integer baseCreditCost;
    private Integer complexityUnits;
    private Integer includedUnits;
    private Integer unitsPerAdditionalCredit;
    private Integer additionalCredits;
    private Boolean contextIncluded;
    private Integer contextSurcharge;
    private Integer totalCreditCost;
}
