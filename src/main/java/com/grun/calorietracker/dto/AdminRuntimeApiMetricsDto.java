package com.grun.calorietracker.dto;

import java.time.LocalDateTime;

public record AdminRuntimeApiMetricsDto(
        long requests,
        long errors,
        double errorRate,
        long latencyP50Ms,
        long latencyP95Ms,
        long latencyP99Ms,
        long rateLimited,
        long authenticationFailures,
        long authorizationFailures,
        boolean latencyThresholdBreached,
        boolean errorRateThresholdBreached,
        LocalDateTime windowStartedAt
) {
}
