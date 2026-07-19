package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminAiCreditPricingPolicyUpdateRequestDto;
import com.grun.calorietracker.dto.AiCreditCostEstimateDto;
import com.grun.calorietracker.dto.AiCreditPricingPolicyDto;
import com.grun.calorietracker.entity.AiCreditPricingPolicyEntity;
import com.grun.calorietracker.enums.AiCreditPricingMode;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.repository.AiCreditPricingPolicyRepository;
import com.grun.calorietracker.service.AiCreditPricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiCreditPricingServiceImpl implements AiCreditPricingService {
    private final AiCreditPricingPolicyRepository repository;

    @Override
    public List<AiCreditPricingPolicyDto> listPolicies() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(item -> item.getFeature().name()))
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public AiCreditPricingPolicyDto updatePolicy(
            SubscriptionFeature feature,
            AdminAiCreditPricingPolicyUpdateRequestDto request) {
        if (!isAiFeature(feature)) {
            throw new IllegalArgumentException("Credit pricing can only be configured for AI features.");
        }
        if (request.getMaxCreditCost() < request.getBaseCreditCost()) {
            throw new IllegalArgumentException("Maximum credit cost cannot be lower than base credit cost.");
        }
        AiCreditPricingPolicyEntity entity = repository.findByFeature(feature)
                .orElseGet(() -> defaultPolicy(feature));
        entity.setBaseCreditCost(request.getBaseCreditCost());
        entity.setIncludedUnits(request.getIncludedUnits());
        entity.setUnitsPerAdditionalCredit(request.getUnitsPerAdditionalCredit());
        entity.setContextSurcharge(request.getContextSurcharge());
        entity.setMaxCreditCost(entity.getPricingMode() == AiCreditPricingMode.FIXED
                ? request.getBaseCreditCost()
                : request.getMaxCreditCost());
        entity.setUpdatedAt(LocalDateTime.now());
        return toDto(repository.save(entity));
    }

    @Override
    public int fixedCost(SubscriptionFeature feature) {
        AiCreditPricingPolicyEntity policy = policy(feature);
        return Math.min(policy.getBaseCreditCost(), policy.getMaxCreditCost());
    }

    @Override
    public AiCreditCostEstimateDto estimateNutrition(
            int dayCount, int mealsPerDay, boolean workoutAligned) {
        if (dayCount < 1 || dayCount > 7) {
            throw new IllegalArgumentException("Nutrition-plan day count must be between 1 and 7.");
        }
        if (mealsPerDay < 1 || mealsPerDay > 6) {
            throw new IllegalArgumentException("Nutrition-plan meals per day must be between 1 and 6.");
        }
        return estimate(
                policy(SubscriptionFeature.AI_NUTRITION_PLAN),
                Math.multiplyExact(dayCount, mealsPerDay),
                workoutAligned);
    }

    @Override
    public AiCreditCostEstimateDto estimateWorkout(
            int daysPerWeek, int minutesPerSession) {
        if (daysPerWeek < 1 || daysPerWeek > 6) {
            throw new IllegalArgumentException("Workout days per week must be between 1 and 6.");
        }
        if (minutesPerSession < 10 || minutesPerSession > 75) {
            throw new IllegalArgumentException("Workout session length must be between 10 and 75 minutes.");
        }
        return estimate(
                policy(SubscriptionFeature.AI_WORKOUT_PLANNER),
                Math.multiplyExact(daysPerWeek, minutesPerSession),
                false);
    }

    private AiCreditCostEstimateDto estimate(
            AiCreditPricingPolicyEntity policy, int complexityUnits, boolean contextIncluded) {
        int billableUnits = Math.max(0, complexityUnits - policy.getIncludedUnits());
        int additionalCredits = billableUnits == 0
                ? 0
                : Math.floorDiv(billableUnits - 1, policy.getUnitsPerAdditionalCredit()) + 1;
        int surcharge = contextIncluded ? policy.getContextSurcharge() : 0;
        int total = Math.min(
                policy.getMaxCreditCost(),
                Math.addExact(policy.getBaseCreditCost(), Math.addExact(additionalCredits, surcharge)));
        return new AiCreditCostEstimateDto(
                policy.getFeature(),
                policy.getBaseCreditCost(),
                complexityUnits,
                policy.getIncludedUnits(),
                policy.getUnitsPerAdditionalCredit(),
                additionalCredits,
                contextIncluded,
                surcharge,
                total);
    }

    private AiCreditPricingPolicyEntity policy(SubscriptionFeature feature) {
        return repository.findByFeature(feature).orElseGet(() -> defaultPolicy(feature));
    }

    private AiCreditPricingPolicyEntity defaultPolicy(SubscriptionFeature feature) {
        AiCreditPricingPolicyEntity entity = new AiCreditPricingPolicyEntity();
        entity.setFeature(feature);
        entity.setBaseCreditCost(1);
        entity.setContextSurcharge(0);
        entity.setUpdatedAt(LocalDateTime.now());
        if (feature == SubscriptionFeature.AI_NUTRITION_PLAN) {
            entity.setPricingMode(AiCreditPricingMode.NUTRITION_COMPLEXITY);
            entity.setIncludedUnits(2);
            entity.setUnitsPerAdditionalCredit(6);
            entity.setContextSurcharge(2);
            entity.setMaxCreditCost(10);
        } else if (feature == SubscriptionFeature.AI_WORKOUT_PLANNER) {
            entity.setPricingMode(AiCreditPricingMode.WORKOUT_COMPLEXITY);
            entity.setIncludedUnits(30);
            entity.setUnitsPerAdditionalCredit(90);
            entity.setMaxCreditCost(6);
        } else {
            entity.setPricingMode(AiCreditPricingMode.FIXED);
            entity.setIncludedUnits(0);
            entity.setUnitsPerAdditionalCredit(1);
            entity.setMaxCreditCost(1);
        }
        return entity;
    }

    private boolean isAiFeature(SubscriptionFeature feature) {
        return switch (feature) {
            case AI_MEAL_DRAFTS, AI_WORKOUT_PLANNER, AI_RECIPE_GENERATION,
                    AI_MEAL_PREPARATION_GUIDE, AI_NUTRITION_PLAN, AI_INSIGHTS -> true;
            default -> false;
        };
    }

    private AiCreditPricingPolicyDto toDto(AiCreditPricingPolicyEntity entity) {
        AiCreditPricingPolicyDto dto = new AiCreditPricingPolicyDto();
        dto.setFeature(entity.getFeature());
        dto.setPricingMode(entity.getPricingMode());
        dto.setBaseCreditCost(entity.getBaseCreditCost());
        dto.setIncludedUnits(entity.getIncludedUnits());
        dto.setUnitsPerAdditionalCredit(entity.getUnitsPerAdditionalCredit());
        dto.setContextSurcharge(entity.getContextSurcharge());
        dto.setMaxCreditCost(entity.getMaxCreditCost());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
