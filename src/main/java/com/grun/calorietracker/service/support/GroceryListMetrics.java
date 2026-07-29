package com.grun.calorietracker.service.support;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroceryListMetrics {

    private static final String METER_NAME = "grun.grocery.operations";
    private final MeterRegistry meterRegistry;

    public void recordOpened() {
        record("opened", "success");
    }

    public void recordGenerated() {
        record("generated", "success");
    }

    public void recordRefreshed() {
        record("refreshed", "success");
    }

    public void recordCompleted() {
        record("completed", "success");
    }

    public void recordRefreshConflict() {
        record("refresh", "conflict");
    }

    public void recordItemMutationConflict() {
        record("item_mutation", "conflict");
    }

    private void record(String action, String result) {
        meterRegistry.counter(METER_NAME, "action", action, "result", result).increment();
    }
}
