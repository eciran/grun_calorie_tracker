package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Privacy-safe onboarding funnel event counts for an admin-selected time window.")
public record AdminOnboardingAnalyticsDto(
        int hours,
        LocalDateTime since,
        LocalDateTime generatedAt,
        long started,
        long stepViewed,
        long stepCompleted,
        long stepFailed,
        long resumed,
        long previewed,
        long completed,
        long abandoned
) {
}
