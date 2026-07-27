package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Privacy-safe user registration and activity counts for one analytics day.")
public record AdminUserAnalyticsDayDto(
        LocalDate date,
        long registrations,
        long activeUsers
) {
}
