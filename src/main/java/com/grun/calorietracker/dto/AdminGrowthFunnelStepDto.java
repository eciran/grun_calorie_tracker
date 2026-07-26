package com.grun.calorietracker.dto;

public record AdminGrowthFunnelStepDto(
        String key,
        String label,
        long users,
        double conversionFromRegistrationPercent,
        String dataStatus,
        String targetSection
) {
}
