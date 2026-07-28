package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FastingDiaryConflictResolution;
import com.grun.calorietracker.enums.FastingDiaryWindowType;

import java.time.LocalDateTime;
import java.util.List;

public record FastingDiaryConflictDto(
        boolean conflict,
        Long occurrenceId,
        Long sessionId,
        FastingDiaryWindowType windowType,
        LocalDateTime plannedStartAt,
        LocalDateTime plannedEndAt,
        List<FastingDiaryConflictResolution> allowedActions) {
}