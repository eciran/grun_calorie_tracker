package com.grun.calorietracker.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeApiMetricsServiceTest {
    @Test
    void aggregatesLatencyErrorsRateLimitsAndSecuritySignals() {
        RuntimeApiMetricsService service = new RuntimeApiMetricsService();
        service.record(10, 200);
        service.record(50, 401);
        service.record(100, 403);
        service.record(500, 429);
        service.record(1000, 503);

        var result = service.snapshot(400, 0.10);

        assertEquals(5, result.requests());
        assertEquals(1, result.errors());
        assertEquals(1, result.rateLimited());
        assertEquals(1, result.authenticationFailures());
        assertEquals(1, result.authorizationFailures());
        assertTrue(result.latencyThresholdBreached());
        assertTrue(result.errorRateThresholdBreached());
    }

    @Test
    void exposesOnlyRecordedHourlyTrendWithoutInventingHistory() {
        RuntimeApiMetricsService service = new RuntimeApiMetricsService();
        service.record(40, 200);
        service.record(400, 500);

        var trend = service.trend(24);

        assertEquals(1, trend.size());
        assertEquals(2, trend.get(0).requests());
        assertEquals(1, trend.get(0).errors());
        assertEquals(400, trend.get(0).latencyP95Ms());
    }}
