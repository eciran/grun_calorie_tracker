package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.service.ProductNutritionOcrReconciler;
import com.grun.calorietracker.service.model.ProductNutritionOcrReconciliation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class ProductNutritionOcrReconcilerImpl implements ProductNutritionOcrReconciler {
    private static final double AGREEMENT_CONFIDENCE_DELTA = 0.05;
    private static final Map<String, String> LOCAL_ALIASES = Map.of(
            "energy", "calories", "carbohydrate", "carbs", "fibre", "fiber");

    @Override
    public ProductNutritionOcrReconciliation reconcile(
            Map<String, Object> localFields, Map<String, Object> geminiFields) {
        Map<String, ProductNutritionOcrReconciliation.FieldDecision> decisions = new LinkedHashMap<>();
        geminiFields.forEach((geminiField, geminiEvidence) -> {
            String localField = localFields.containsKey(geminiField)
                    ? geminiField : LOCAL_ALIASES.getOrDefault(geminiField, geminiField);
            Object localValue = localFields.get(localField);
            Object geminiValue = rawValue(geminiEvidence);
            boolean agreed = !normalized(localValue).isBlank()
                    && normalized(localValue).equals(normalized(geminiValue));
            decisions.put(geminiField, new ProductNutritionOcrReconciliation.FieldDecision(
                    localField, localValue, geminiValue,
                    agreed ? ProductNutritionOcrReconciliation.Decision.AGREED
                            : ProductNutritionOcrReconciliation.Decision.REVIEW,
                    agreed ? AGREEMENT_CONFIDENCE_DELTA : 0,
                    agreed ? "LOCAL_GEMINI_AGREEMENT" : "LOCAL_GEMINI_MISMATCH"));
        });
        return new ProductNutritionOcrReconciliation(Map.copyOf(decisions));
    }

    private Object rawValue(Object evidence) {
        return evidence instanceof Map<?, ?> map ? map.get("rawValue") : evidence;
    }

    private String normalized(Object value) {
        return Objects.toString(value, "").trim().replace(',', '.').toLowerCase(Locale.ROOT);
    }
}
