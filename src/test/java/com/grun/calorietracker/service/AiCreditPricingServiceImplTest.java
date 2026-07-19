package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiCreditCostEstimateDto;
import com.grun.calorietracker.entity.AiCreditPricingPolicyEntity;
import com.grun.calorietracker.enums.AiCreditPricingMode;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.repository.AiCreditPricingPolicyRepository;
import com.grun.calorietracker.service.impl.AiCreditPricingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiCreditPricingServiceImplTest {
    private AiCreditPricingPolicyRepository repository;
    private AiCreditPricingServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(AiCreditPricingPolicyRepository.class);
        service = new AiCreditPricingServiceImpl(repository);
        when(repository.findByFeature(SubscriptionFeature.AI_NUTRITION_PLAN))
                .thenReturn(Optional.of(policy(SubscriptionFeature.AI_NUTRITION_PLAN,
                        AiCreditPricingMode.NUTRITION_COMPLEXITY, 1, 2, 6, 2, 10)));
        when(repository.findByFeature(SubscriptionFeature.AI_WORKOUT_PLANNER))
                .thenReturn(Optional.of(policy(SubscriptionFeature.AI_WORKOUT_PLANNER,
                        AiCreditPricingMode.WORKOUT_COMPLEXITY, 1, 30, 90, 0, 6)));
    }

    @Test
    void nutritionPricingScalesWithMealSlots() {
        assertEquals(1, service.estimateNutrition(1, 2, false).getTotalCreditCost());
        assertEquals(3, service.estimateNutrition(3, 3, false).getTotalCreditCost());
        assertEquals(8, service.estimateNutrition(7, 6, false).getTotalCreditCost());
    }

    @Test
    void nutritionWorkoutContextAddsConfiguredSurcharge() {
        AiCreditCostEstimateDto estimate = service.estimateNutrition(7, 6, true);

        assertEquals(42, estimate.getComplexityUnits());
        assertEquals(7, estimate.getAdditionalCredits());
        assertEquals(2, estimate.getContextSurcharge());
        assertEquals(10, estimate.getTotalCreditCost());
    }

    @Test
    void workoutPricingScalesWithTotalPlannedMinutes() {
        assertEquals(1, service.estimateWorkout(1, 10).getTotalCreditCost());
        assertEquals(3, service.estimateWorkout(3, 45).getTotalCreditCost());
        assertEquals(6, service.estimateWorkout(6, 75).getTotalCreditCost());
    }

    @Test
    void rejectsValuesOutsideSupportedProductLimits() {
        assertThrows(IllegalArgumentException.class, () -> service.estimateNutrition(8, 3, false));
        assertThrows(IllegalArgumentException.class, () -> service.estimateNutrition(2, 7, false));
        assertThrows(IllegalArgumentException.class, () -> service.estimateWorkout(7, 45));
        assertThrows(IllegalArgumentException.class, () -> service.estimateWorkout(3, 76));
    }

    private AiCreditPricingPolicyEntity policy(
            SubscriptionFeature feature,
            AiCreditPricingMode mode,
            int base,
            int included,
            int unitSize,
            int surcharge,
            int maximum) {
        AiCreditPricingPolicyEntity entity = new AiCreditPricingPolicyEntity();
        entity.setFeature(feature);
        entity.setPricingMode(mode);
        entity.setBaseCreditCost(base);
        entity.setIncludedUnits(included);
        entity.setUnitsPerAdditionalCredit(unitSize);
        entity.setContextSurcharge(surcharge);
        entity.setMaxCreditCost(maximum);
        return entity;
    }
}