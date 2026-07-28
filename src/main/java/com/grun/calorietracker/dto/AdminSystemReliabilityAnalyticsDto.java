package com.grun.calorietracker.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminSystemReliabilityAnalyticsDto(
        int windowHours,
        LocalDateTime generatedAt,
        LocalDateTime apiWindowStartedAt,
        List<ApiTrendPoint> apiTrend,
        List<InfrastructureMetric> infrastructure,
        List<ProviderMetric> providers,
        List<OperationMetric> operations,
        List<OperationTrendPoint> operationTrend
) {
    public record ApiTrendPoint(
            LocalDateTime bucket,
            long requests,
            long errors,
            double errorRate,
            long latencyP95Ms
    ) {}

    public record InfrastructureMetric(
            String component,
            String status,
            Long latencyMs,
            Double utilizationPercent
    ) {}

    public record ProviderMetric(
            String provider,
            String status,
            long attempts,
            long successes,
            long failures,
            Double successRate
    ) {}

    public record OperationMetric(
            String recordType,
            long total,
            long succeeded,
            long failed,
            long deadLetters,
            long open
    ) {}

    public record OperationTrendPoint(
            String date,
            long succeeded,
            long failed,
            long deadLetters
    ) {}
}