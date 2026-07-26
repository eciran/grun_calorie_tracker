package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Schema(description = "Privacy-safe executive growth dashboard aggregates for one reporting period.")
public record AdminDashboardGrowthDto(
        LocalDate from,
        LocalDate to,
        LocalDate previousFrom,
        LocalDate previousTo,
        String timeZone,
        Instant generatedAt,
        int rangeDays,
        long legacyUsersWithoutRegistrationDate,
        double registrationCoveragePercent,
        List<AdminGrowthKpiDto> kpis,
        List<AdminGrowthTrendPointDto> daily,
        List<AdminGrowthFunnelStepDto> funnel,
        Map<String, Long> planDistribution,
        Map<String, Long> regionDistribution,
        Map<String, Long> languageDistribution
) {
}
