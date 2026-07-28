package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record FastingDiaryConflictEvaluateRequestDto(
        @NotNull LocalDateTime loggedAt,
        String mealType) {
}