package com.grun.calorietracker.service.model;

import java.util.Map;

public record ProductNutritionOcrReconciliation(Map<String, FieldDecision> fields) {
    public enum Decision { AGREED, REVIEW }

    public record FieldDecision(
            String localField,
            Object localValue,
            Object geminiValue,
            Decision decision,
            double confidenceDelta,
            String reason
    ) { }
}
