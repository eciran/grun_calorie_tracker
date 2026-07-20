package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminAiCreditPricingPolicyUpdateRequestDto;
import com.grun.calorietracker.dto.AiCreditCostEstimateDto;
import com.grun.calorietracker.dto.AiCreditPricingPolicyDto;
import com.grun.calorietracker.enums.SubscriptionFeature;

import java.util.List;

public interface AiCreditPricingService {
    List<AiCreditPricingPolicyDto> listPolicies();

    AiCreditPricingPolicyDto updatePolicy(
            SubscriptionFeature feature,
            AdminAiCreditPricingPolicyUpdateRequestDto request);

    int fixedCost(SubscriptionFeature feature);

    AiCreditCostEstimateDto estimateNutrition(int dayCount, int mealsPerDay, boolean workoutAligned);

    AiCreditCostEstimateDto estimateWorkout(int daysPerWeek, int minutesPerSession);
}
