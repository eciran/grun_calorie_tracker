package com.grun.calorietracker.dto;

import java.util.Map;

public record AdminTestFeedbackAnalyticsDto(
        long total, long lastSevenDays, Map<String, Long> byStatus,
        Map<String, Long> byType, Map<String, Long> byPlatform
) { }
