package com.grun.calorietracker.service.model;

import java.util.Map;

public record ProductNutritionOcrShadowRequest(
        ProductNutritionOcrFallbackRequest fallbackRequest,
        Map<String, Object> displayedFields,
        Map<String, Object> v3Fields,
        Map<String, Object> v4Fields,
        Map<String, Object> userConfirmedFields
) {
    public ProductNutritionOcrShadowRequest {
        displayedFields = copy(displayedFields);
        v3Fields = copy(v3Fields);
        v4Fields = copy(v4Fields);
        userConfirmedFields = copy(userConfirmedFields);
    }

    private static Map<String, Object> copy(Map<String, Object> value) {
        return value == null ? Map.of() : Map.copyOf(value);
    }
}
