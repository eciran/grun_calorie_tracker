package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FastingDiaryConflictResolution;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record FastingDiaryConflictResolveRequestDto(
        @NotNull LocalDateTime loggedAt,
        Long occurrenceId,
        Long sessionId,
        @NotNull FastingDiaryConflictResolution resolution,
        @Valid FoodLogsDto foodLog) {
}