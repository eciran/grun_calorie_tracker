package com.grun.calorietracker.service.support;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GroceryListMetricsTest {

    @Test
    void recordsOnlyBoundedAggregateTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GroceryListMetrics metrics = new GroceryListMetrics(registry);

        metrics.recordOpened();
        metrics.recordRefreshConflict();

        assertEquals(1.0, registry.counter("grun.grocery.operations",
                "action", "opened", "result", "success").count());
        assertEquals(1.0, registry.counter("grun.grocery.operations",
                "action", "refresh", "result", "conflict").count());
        registry.getMeters().forEach(meter -> assertFalse(meter.getId().getTags().stream()
                .anyMatch(tag -> tag.getKey().equals("user") || tag.getKey().equals("listId")
                        || tag.getKey().equals("itemId"))));
    }
}
