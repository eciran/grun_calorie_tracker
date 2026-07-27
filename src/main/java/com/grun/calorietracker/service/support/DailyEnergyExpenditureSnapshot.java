package com.grun.calorietracker.service.support;

import com.grun.calorietracker.enums.EnergyExpenditureSource;

import java.time.LocalDate;

public record DailyEnergyExpenditureSnapshot(
        LocalDate date,
        Double restingEnergyCalories,
        Double activeEnergyCalories,
        Double totalExpenditureCalories,
        EnergyExpenditureSource source
) {
}