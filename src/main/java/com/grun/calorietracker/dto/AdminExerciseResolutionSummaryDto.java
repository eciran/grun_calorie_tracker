package com.grun.calorietracker.dto;

public record AdminExerciseResolutionSummaryDto(long openNames, long openOccurrences,
        long resolvedNames, long dismissedNames, long reviewedNames,
        double resolutionRatePercent) {}
