package com.grun.calorietracker.service;

import com.grun.calorietracker.service.impl.ProductNutritionOcrReconcilerImpl;
import com.grun.calorietracker.service.model.ProductNutritionOcrReconciliation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductNutritionOcrReconcilerImplTest {
    private final ProductNutritionOcrReconciler reconciler = new ProductNutritionOcrReconcilerImpl();

    @Test
    void agreementRaisesConfidenceSignalWithoutReplacingLocalValue() {
        var result = reconciler.reconcile(Map.of("calories", "200"),
                Map.of("energy", evidence("200")));
        var field = result.fields().get("energy");
        assertEquals(ProductNutritionOcrReconciliation.Decision.AGREED, field.decision());
        assertEquals(0.05, field.confidenceDelta());
        assertEquals("200", field.localValue());
    }

    @Test
    void disagreementAndMissingLocalEvidenceAlwaysRequireReview() {
        var mismatch = reconciler.reconcile(Map.of("protein", "6"), Map.of("protein", evidence("9")));
        var missing = reconciler.reconcile(Map.of(), Map.of("fat", evidence("8")));
        assertEquals(ProductNutritionOcrReconciliation.Decision.REVIEW,
                mismatch.fields().get("protein").decision());
        assertEquals(0, mismatch.fields().get("protein").confidenceDelta());
        assertEquals(ProductNutritionOcrReconciliation.Decision.REVIEW,
                missing.fields().get("fat").decision());
    }

    private Map<String, Object> evidence(String rawValue) {
        return Map.of("rawValue", rawValue, "unit", "g", "basis", "PER_100G");
    }
}
