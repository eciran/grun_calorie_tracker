package com.grun.calorietracker.dto;

import java.util.Map;

public record AdminTestFeedbackAnalyticsDto(
        long total,
        long lastSevenDays,
        long httpFailures,
        long slowRequests,
        Map<String, Long> byStatus,
        Map<String, Long> byType,
        Map<String, Long> byPlatform,
        Map<String, Long> byRoute,
        Map<String, Long> byBuild
) { }
