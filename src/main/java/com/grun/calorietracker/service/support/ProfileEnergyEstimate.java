package com.grun.calorietracker.service.support;

public record ProfileEnergyEstimate(
        double restingEnergyCalories,
        double totalDailyEnergyCalories,
        String formula
) {
}