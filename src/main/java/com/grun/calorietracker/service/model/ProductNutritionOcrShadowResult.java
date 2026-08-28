package com.grun.calorietracker.service.model;

import java.util.Map;

public record ProductNutritionOcrShadowResult(
        Map<String, Object> displayedFields,
        Map<String, Object> geminiFields,
        boolean fallbackInvoked,
        double v3ExactMatchRate,
        double v4ExactMatchRate,
        double geminiExactMatchRate,
        boolean v3BasisExact,
        boolean v4BasisExact,
        boolean geminiBasisExact,
        long latencyMs,
        double estimatedCostUsd,
        ProductNutritionOcrReconciliation reconciliation
) {
}
