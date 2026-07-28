package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FastingDiaryConflictResolution;

public record FastingDiaryConflictResolutionDto(
        FastingDiaryConflictResolution resolution,
        FoodLogsDto foodLog) {
}