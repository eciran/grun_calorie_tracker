package com.grun.calorietracker.service.support;

import com.grun.calorietracker.enums.EnergyExpenditureSource;
import com.grun.calorietracker.enums.HealthProvider;

import java.time.LocalDate;

public record HealthDailyEnergySnapshot(
        LocalDate date,
        HealthProvider provider,
        Double activeEnergyCalories,
        Double restingEnergyCalories,
        Double totalEnergyCalories,
        EnergyExpenditureSource expenditureSource
) {
}