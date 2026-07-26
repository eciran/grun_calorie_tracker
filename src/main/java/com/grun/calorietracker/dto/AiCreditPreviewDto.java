package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.SubscriptionFeature;
import lombok.Data;

@Data
public class AiCreditPreviewDto {
    private AiRequestType requestType;
    private SubscriptionFeature feature;
    private int creditCost;
    private Integer aiBaseRemainingThisPeriod;
    private Integer aiAddonRemainingThisPeriod;
    private Integer aiRemainingThisPeriod;
    private boolean canAfford;
    private boolean confirmationRequired;
}