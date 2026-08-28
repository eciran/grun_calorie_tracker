package com.grun.calorietracker.service.model;

import java.util.Map;

public record ProductNutritionOcrFallbackResult(
        String provider,
        String model,
        Map<String, Object> fields
) {
}
