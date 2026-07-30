package com.grun.calorietracker.service;

import com.grun.calorietracker.service.support.FoodProductIntakeMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoodProductIntakeMetricsTest {
    @Test
    void recordsBoundedOperationCleanupAndRolloutOutcomes() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FoodProductIntakeMetrics metrics = new FoodProductIntakeMetrics(registry);
        metrics.record("upload_finalize", "success");
        metrics.recordCleanup(2, 1);
        metrics.recordRolloutDecision("internal_dogfood", "global");
        assertEquals(1.0, registry.get("grun.food.product.intake.operations")
                .tags("action", "upload_finalize", "result", "success").counter().count());
        assertEquals(2.0, registry.get("grun.food.product.intake.operations")
                .tags("action", "cleanup", "result", "deleted").counter().count());
        assertEquals(1.0, registry.get("grun.food.product.intake.operations")
                .tags("action", "cleanup", "result", "failed").counter().count());
        assertEquals(1.0, registry.get("grun.food.product.intake.rollout.decisions")
                .tags("reason", "internal_dogfood", "market", "global").counter().count());
    }
}