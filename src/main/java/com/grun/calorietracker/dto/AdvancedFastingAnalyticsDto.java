package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FastingSessionOutcomeReason;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AdvancedFastingAnalyticsDto(
        LocalDate startDate,
        LocalDate endDate,
        int requestedDays,
        int occurrenceCount,
        int evaluableOccurrenceCount,
        boolean minimumDataMet,
        int minimumRecommendedOccurrences,
        List<String> dataQualityIndicators,
        Double adherencePercent,
        Double scheduleConsistencyPercent,
        Double averageStartDeviationMinutes,
        Double averageDurationMinutes,
        Integer earlyStopCount,
        Double earlyStopPercent,
        Double fiveTwoAdherencePercent,
        List<WeekdayTrend> weekdayTrends,
        Map<FastingSessionOutcomeReason, Integer> stopReasonDistribution
) {
    public record WeekdayTrend(
            DayOfWeek dayOfWeek,
            int plannedOccurrences,
            int evaluableOccurrences,
            Double adherencePercent,
            Double averageDurationMinutes
    ) {
    }
}