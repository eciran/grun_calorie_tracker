package com.grun.calorietracker.service;

import com.grun.calorietracker.config.EnergyBalanceAnalyticsProperties;
import com.grun.calorietracker.enums.EnergyBalanceState;
import com.grun.calorietracker.enums.EnergyDataConfidence;
import com.grun.calorietracker.service.support.EnergyBalancePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnergyBalancePolicyTest {

    private EnergyBalancePolicy policy;

    @BeforeEach
    void setUp() {
        policy = new EnergyBalancePolicy(new EnergyBalanceAnalyticsProperties());
    }

    @Test
    void minimumWeightModelDays_usesDedicatedThreshold() {
        EnergyBalanceAnalyticsProperties properties = new EnergyBalanceAnalyticsProperties();
        properties.setMinimumWeightModelDays(7);
        properties.setMinimumEvaluatedDaysForConfidence(3);

        assertEquals(7, new EnergyBalancePolicy(properties).minimumWeightModelDays());
    }
    @Test
    void validateRange_acceptsInclusiveRange() {
        int days = policy.validateRange(
                LocalDate.of(2025, 7, 27),
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 7, 27)
        );

        assertEquals(366, days);
    }

    @Test
    void validateRange_rejectsFutureReversedAndOverLimitRanges() {
        LocalDate today = LocalDate.of(2026, 7, 27);

        assertThrows(IllegalArgumentException.class,
                () -> policy.validateRange(today, today.plusDays(1), today));
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateRange(today, today.minusDays(1), today));
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateRange(today.minusDays(366), today, today));
    }

    @Test
    void resolveBalanceState_usesAbsoluteOrRelativeTolerance() {
        assertEquals(EnergyBalanceState.INSUFFICIENT_DATA,
                policy.resolveBalanceState(null, 2000.0));
        assertEquals(EnergyBalanceState.BALANCED,
                policy.resolveBalanceState(2080.0, 2000.0));
        assertEquals(EnergyBalanceState.BALANCED,
                policy.resolveBalanceState(2110.0, 2200.0));
        assertEquals(EnergyBalanceState.DEFICIT,
                policy.resolveBalanceState(1800.0, 2200.0));
        assertEquals(EnergyBalanceState.SURPLUS,
                policy.resolveBalanceState(2500.0, 2200.0));
    }

    @Test
    void resolveConfidence_combinesCoverageSampleCountAndProviderShare() {
        assertEquals(EnergyDataConfidence.INSUFFICIENT,
                policy.resolveConfidence(30, 0, 30, 30));
        assertEquals(EnergyDataConfidence.LOW,
                policy.resolveConfidence(30, 3, 30, 30));
        assertEquals(EnergyDataConfidence.MEDIUM,
                policy.resolveConfidence(30, 18, 30, 18));
        assertEquals(EnergyDataConfidence.MEDIUM,
                policy.resolveConfidence(30, 25, 30, 10));
        assertEquals(EnergyDataConfidence.HIGH,
                policy.resolveConfidence(30, 25, 30, 18));
    }

    @Test
    void weightModelParameters_areExplicitAndVersioned() {
        assertEquals("STATIC_ENERGY_DENSITY_V1", policy.weightModelCode());
        assertEquals(7700.0, policy.energyPerKgCoefficient());
        assertEquals(4, policy.minimumWeightModelDays());
    }
}
