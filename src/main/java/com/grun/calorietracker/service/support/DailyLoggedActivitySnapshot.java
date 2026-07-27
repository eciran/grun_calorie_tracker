package com.grun.calorietracker.service.support;

import java.time.LocalDate;

public record DailyLoggedActivitySnapshot(
        LocalDate date,
        Double exerciseCalories,
        Double stepCalories
) {
    public double totalCalories() {
        return safe(exerciseCalories) + safe(stepCalories);
    }

    private double safe(Double value) {
        return value == null || !Double.isFinite(value) ? 0.0 : Math.max(0.0, value);
    }
}
