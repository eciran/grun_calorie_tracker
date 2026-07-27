package com.grun.calorietracker.service.support;

import java.time.LocalDate;

public record DailyCalorieIntakeSnapshot(
        LocalDate date,
        Double consumedCalories,
        boolean foodLogged
) {
}