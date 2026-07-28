package com.grun.calorietracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdvancedFastingOperationsConfigRequestDto(
        @NotNull Boolean reminderEnabled,
        @NotNull @Min(10) @Max(60) Integer preStartMinutes,
        @NotNull @Min(10) @Max(60) Integer nearingCompletionMinutes,
        @NotNull @Min(30) @Max(180) Integer missedPlanMinutes,
        @NotNull @Min(1) @Max(5) Integer maxRetryAttempts
) {
}