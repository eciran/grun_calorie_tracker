package com.grun.calorietracker.service.model;

import java.util.List;
import java.util.Map;

public record ProductNutritionOcrPreparedEvidence(
        ProductNutritionOcrEvidence evidence,
        List<Map<String, Object>> wordBoxes
) {
}
