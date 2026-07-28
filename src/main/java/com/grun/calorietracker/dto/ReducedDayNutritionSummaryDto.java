package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FastingAdherenceStatus;
import java.time.*;

public record ReducedDayNutritionSummaryDto(
        Long occurrenceId,
        LocalDate date,
        Integer plannedCalories,
        Double consumedCalories,
        Double remainingCalories,
        LocalDateTime lastEvaluatedAt,
        FastingAdherenceStatus adherenceStatus) {}