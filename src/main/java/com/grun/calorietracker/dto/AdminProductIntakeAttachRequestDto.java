package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdminProductIntakeAttachRequestDto(@NotNull @Positive Long foodItemId) {
}