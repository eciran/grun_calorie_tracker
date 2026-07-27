package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.SubscriptionPlan;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Schema(description = "Privacy-safe user growth, activity, and current plan aggregates.")
public record AdminUserAnalyticsDto(
        LocalDate from,
        LocalDate to,
        String timeZone,
        String granularity,
        Instant generatedAt,
        long totalUsers,
        long legacyUsersWithoutRegistrationDate,
        long registrationsInRange,
        long activeUsersInRange,
        long dailyActiveUsers,
        long weeklyActiveUsers,
        long monthlyActiveUsers,
        Map<SubscriptionPlan, Long> planDistribution,
        List<AdminUserAnalyticsDayDto> daily
) {
}
