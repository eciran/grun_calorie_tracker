package com.grun.calorietracker.service;

import com.grun.calorietracker.exception.AiProviderException;
import com.grun.calorietracker.service.impl.ProductNutritionOcrResultValidator;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductNutritionOcrResultValidatorTest {
    private final ProductNutritionOcrResultValidator validator = new ProductNutritionOcrResultValidator();
    private final ProductNutritionOcrFallbackRequest request = new ProductNutritionOcrFallbackRequest(
            1L, 2L, 3L, "v4", 0.5, List.of("energy"), List.of(), true, "ai-v1");

    @Test
    void acceptsEvidenceBackedPlausibleValue() {
        Map<String, Object> fields = Map.of("energy", field("200", "kcal", "PER_100G"));
        assertEquals(fields, validator.validate(request, fields));
    }

    @Test
    void rejectsSchemaValidButIllogicalValue() {
        Map<String, Object> fields = Map.of("energy", field("9000", "kcal", "PER_100G"));
        assertThrows(AiProviderException.class, () -> validator.validate(request, fields));
    }

    @Test
    void rejectsFieldOutsideRequestedUncertainSet() {
        Map<String, Object> fields = Map.of("fat", field("8", "g", "PER_100G"));
        assertThrows(AiProviderException.class, () -> validator.validate(request, fields));
    }

    private Map<String, Object> field(String value, String unit, String basis) {
        return Map.of("rawValue", value, "unit", unit, "basis", basis,
                "evidenceBox", Map.of("x", 0.2, "y", 0.3, "width", 0.1, "height", 0.05));
    }
}
