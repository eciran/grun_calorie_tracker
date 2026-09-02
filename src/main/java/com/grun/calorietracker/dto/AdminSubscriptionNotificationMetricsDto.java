package com.grun.calorietracker.dto;

import java.util.Map;

public record AdminSubscriptionNotificationMetricsDto(
        long totalOccurrences,
        Map<String, Long> occurrenceStatuses,
        Map<String, Long> outboxStatuses,
        Map<String, Long> attemptStatuses,
        long opened,
        long clicked,
        long dismissed) { }
