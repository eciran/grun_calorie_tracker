package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Decision-ready growth KPI with explicit comparison and data coverage state.")
public record AdminGrowthKpiDto(
        String key,
        String label,
        double value,
        String unit,
        Double previousValue,
        Double changePercent,
        boolean comparisonAvailable,
        String dataStatus,
        String detail,
        String targetSection
) {
}
