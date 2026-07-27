package com.grun.calorietracker.service.support;

import com.grun.calorietracker.config.EnergyBalanceAnalyticsProperties;
import com.grun.calorietracker.enums.EnergyBalanceState;
import com.grun.calorietracker.enums.EnergyDataConfidence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class EnergyBalancePolicy {

    private final EnergyBalanceAnalyticsProperties properties;

    public int validateRange(LocalDate startDate, LocalDate endDate, LocalDate userToday) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Energy balance requires both start and end dates.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Energy balance end date must not be before start date.");
        }
        if (userToday != null && endDate.isAfter(userToday)) {
            throw new IllegalArgumentException("Energy balance end date must not be in the future.");
        }
        long dayCount = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (dayCount > properties.getMaxRangeDays()) {
            throw new IllegalArgumentException(
                    "Energy balance date range must not exceed " + properties.getMaxRangeDays() + " days.");
        }
        return Math.toIntExact(dayCount);
    }

    public EnergyBalanceState resolveBalanceState(Double consumedCalories, Double expenditureCalories) {
        if (consumedCalories == null || expenditureCalories == null || expenditureCalories <= 0.0) {
            return EnergyBalanceState.INSUFFICIENT_DATA;
        }
        double balance = consumedCalories - expenditureCalories;
        double tolerance = Math.max(
                properties.getBalancedAbsoluteToleranceKcal(),
                expenditureCalories * properties.getBalancedRelativeTolerance()
        );
        if (Math.abs(balance) <= tolerance) {
            return EnergyBalanceState.BALANCED;
        }
        return balance < 0.0 ? EnergyBalanceState.DEFICIT : EnergyBalanceState.SURPLUS;
    }

    public EnergyDataConfidence resolveConfidence(
            int rangeDayCount,
            int evaluatedDays,
            int expenditureAvailableDays,
            int healthProviderDays
    ) {
        if (rangeDayCount <= 0 || evaluatedDays <= 0) {
            return EnergyDataConfidence.INSUFFICIENT;
        }
        double coveragePercent = percent(evaluatedDays, rangeDayCount);
        if (evaluatedDays < properties.getMinimumEvaluatedDaysForConfidence()
                || coveragePercent < properties.getMediumCoveragePercent()) {
            return EnergyDataConfidence.LOW;
        }
        double providerShare = expenditureAvailableDays <= 0
                ? 0.0
                : healthProviderDays / (double) expenditureAvailableDays;
        if (coveragePercent >= properties.getHighCoveragePercent()
                && providerShare >= properties.getHighProviderShare()) {
            return EnergyDataConfidence.HIGH;
        }
        return EnergyDataConfidence.MEDIUM;
    }

    public String weightModelCode() {
        return properties.getWeightModelCode();
    }

    public double energyPerKgCoefficient() {
        return properties.getEnergyPerKgCoefficient();
    }

    public double weightModelUncertaintyPercent() {
        return properties.getWeightModelUncertaintyPercent();
    }
    public int minimumWeightModelDays() {
        return properties.getMinimumWeightModelDays();
    }

    private double percent(int numerator, int denominator) {
        return denominator <= 0 ? 0.0 : (numerator * 100.0) / denominator;
    }
}
