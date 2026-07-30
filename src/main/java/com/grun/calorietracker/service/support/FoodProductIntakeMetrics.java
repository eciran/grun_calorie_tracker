package com.grun.calorietracker.service.support;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FoodProductIntakeMetrics {
    private static final String METER_NAME = "grun.food.product.intake.operations";
    private final MeterRegistry meterRegistry;

    public void record(String action, String result) {
        meterRegistry.counter(METER_NAME, "action", action, "result", result).increment();
    }

    public void recordCleanup(int deleted, int failed) {
        meterRegistry.counter(METER_NAME, "action", "cleanup", "result", "deleted").increment(deleted);
        meterRegistry.counter(METER_NAME, "action", "cleanup", "result", "failed").increment(failed);
    }
}