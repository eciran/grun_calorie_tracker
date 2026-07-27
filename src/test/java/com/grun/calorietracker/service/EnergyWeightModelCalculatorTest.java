package com.grun.calorietracker.service;

import com.grun.calorietracker.config.EnergyBalanceAnalyticsProperties;
import com.grun.calorietracker.entity.BodyMeasurementEntity;
import com.grun.calorietracker.enums.EnergyWeightModelStatus;
import com.grun.calorietracker.service.support.EnergyBalancePolicy;
import com.grun.calorietracker.service.support.EnergyWeightModelCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EnergyWeightModelCalculatorTest {

    private final EnergyBalanceAnalyticsProperties properties = new EnergyBalanceAnalyticsProperties();
    private final EnergyWeightModelCalculator calculator = new EnergyWeightModelCalculator(
            new EnergyBalancePolicy(properties));

    @Test
    void calculate_returnsModeledRangeAndObservedChange() {
        var result = calculator.calculate(
                date(1),
                date(14),
                -7700.0,
                14,
                List.of(
                        measurement(82.0, 1, 8),
                        measurement(80.8, 14, 8)
                )
        );

        assertEquals("STATIC_ENERGY_DENSITY_V1", result.getModelCode());
        assertEquals(7700.0, result.getEnergyPerKgCoefficient());
        assertEquals(-1.0, result.getModeledWeightChangeKg());
        assertEquals(-1.15, result.getModeledWeightChangeLowerKg());
        assertEquals(-0.85, result.getModeledWeightChangeUpperKg());
        assertEquals(-1.2, result.getObservedWeightChangeKg());
        assertEquals(-0.2, result.getDifferenceFromModelKg());
        assertEquals(EnergyWeightModelStatus.AVAILABLE, result.getStatus());
    }

    @Test
    void calculate_usesLatestMeasurementWhenDayHasMultipleWeights() {
        var result = calculator.calculate(
                date(1),
                date(7),
                0.0,
                7,
                List.of(
                        measurement(82.0, 1, 7),
                        measurement(81.8, 1, 20),
                        measurement(81.5, 7, 8)
                )
        );

        assertEquals(81.8, result.getStartWeightKg());
        assertEquals(81.5, result.getEndWeightKg());
        assertEquals(-0.3, result.getObservedWeightChangeKg());
    }

    @Test
    void calculate_keepsModeledResultWhenWeightBaselineIsMissing() {
        var result = calculator.calculate(date(1), date(7), -3850.0, 7, List.of());

        assertEquals(-0.5, result.getModeledWeightChangeKg());
        assertNull(result.getObservedWeightChangeKg());
        assertEquals(EnergyWeightModelStatus.MISSING_WEIGHT_BASELINE, result.getStatus());
    }

    @Test
    void calculate_reportsMissingEnergyWithoutTreatingItAsZero() {
        var result = calculator.calculate(
                date(1), date(7), null, 0,
                List.of(measurement(82.0, 1, 8), measurement(81.0, 7, 8)));

        assertNull(result.getModeledWeightChangeKg());
        assertNull(result.getObservedWeightChangeKg());
        assertEquals(EnergyWeightModelStatus.MISSING_ENERGY_DATA, result.getStatus());
    }

    @Test
    void calculate_rejectsModelForShortRange() {
        var result = calculator.calculate(date(1), date(3), -3000.0, 3, List.of());

        assertEquals(EnergyWeightModelStatus.INSUFFICIENT_RANGE, result.getStatus());
        assertNull(result.getModeledWeightChangeKg());
    }

    @Test
    void calculate_ignoresInvalidOutOfRangeAndOutsidePeriodWeights() {
        BodyMeasurementEntity invalid = measurement(600.0, 1, 8);
        BodyMeasurementEntity outside = measurement(82.0, 1, 8);
        outside.setRecordedAt(LocalDateTime.of(2026, 6, 30, 8, 0));
        var result = calculator.calculate(
                date(1), date(7), -1000.0, 7,
                List.of(outside, invalid, measurement(81.0, 7, 8)));

        assertEquals(EnergyWeightModelStatus.MISSING_WEIGHT_BASELINE, result.getStatus());
        assertNull(result.getObservedWeightChangeKg());
    }

    private BodyMeasurementEntity measurement(double weight, int day, int hour) {
        BodyMeasurementEntity measurement = new BodyMeasurementEntity();
        measurement.setWeightKg(weight);
        measurement.setRecordedAt(LocalDateTime.of(2026, 7, day, hour, 0));
        return measurement;
    }

    private LocalDate date(int day) {
        return LocalDate.of(2026, 7, day);
    }
}