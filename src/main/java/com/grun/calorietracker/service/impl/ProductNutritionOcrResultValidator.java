package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.exception.AiProviderException;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackRequest;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class ProductNutritionOcrResultValidator {
    private static final Set<String> BASES = Set.of("PER_100G", "PER_100ML", "PER_SERVING", "RI_PERCENT", "UNKNOWN");
    private static final Set<String> UNITS = Set.of("g", "mg", "µg", "ug", "kj", "kcal", "%");

    public Map<String, Object> validate(ProductNutritionOcrFallbackRequest request, Map<String, Object> fields) {
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!request.uncertainFields().contains(entry.getKey()) || !(entry.getValue() instanceof Map<?, ?> field)) {
                throw invalid("unexpected field");
            }
            Object raw = field.get("rawValue");
            if (raw == null) continue;
            if (!(raw instanceof String text) || !text.matches("[-+]?\\d+(?:[.,]\\d+)?")) throw invalid("raw value");
            double value = Double.parseDouble(text.replace(',', '.'));
            if (!Double.isFinite(value) || value < 0) throw invalid("negative or non-finite value");
            String unit = string(field.get("unit"));
            String basis = string(field.get("basis"));
            if (unit == null || !UNITS.contains(unit.toLowerCase(Locale.ROOT))) throw invalid("unit");
            if (basis == null || !BASES.contains(basis)) throw invalid("basis");
            if ("%".equals(unit) && value > 100) throw invalid("percentage range");
            if (("g".equalsIgnoreCase(unit) || "mg".equalsIgnoreCase(unit) || "µg".equalsIgnoreCase(unit)
                    || "ug".equalsIgnoreCase(unit)) && value > 100_000) throw invalid("nutrient range");
            if ("kcal".equalsIgnoreCase(unit) && value > 5_000) throw invalid("energy range");
            if ("kj".equalsIgnoreCase(unit) && value > 30_000) throw invalid("energy range");
            validateBox(field.get("evidenceBox"));
        }
        return Map.copyOf(fields);
    }

    private void validateBox(Object value) {
        if (!(value instanceof Map<?, ?> box)) throw invalid("evidence box");
        for (String key : Set.of("x", "y", "width", "height")) {
            Object coordinate = box.get(key);
            if (!(coordinate instanceof Number number) || !Double.isFinite(number.doubleValue())
                    || number.doubleValue() < 0 || number.doubleValue() > 1) throw invalid("evidence box");
        }
        double x = ((Number) box.get("x")).doubleValue(), y = ((Number) box.get("y")).doubleValue();
        double width = ((Number) box.get("width")).doubleValue(), height = ((Number) box.get("height")).doubleValue();
        if (width <= 0 || height <= 0 || x + width > 1 || y + height > 1) throw invalid("evidence box bounds");
    }

    private String string(Object value) { return value instanceof String text && !text.isBlank() ? text : null; }
    private AiProviderException invalid(String reason) {
        return new AiProviderException("Gemini product OCR returned invalid " + reason + ".");
    }
}
