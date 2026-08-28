package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.ProductNutritionOcrProperties;
import com.grun.calorietracker.entity.ProductNutritionOcrShadowRunEntity;
import com.grun.calorietracker.repository.ProductNutritionOcrShadowRunRepository;
import com.grun.calorietracker.service.impl.ProductNutritionOcrShadowServiceImpl;
import com.grun.calorietracker.service.impl.ProductNutritionOcrReconcilerImpl;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackRequest;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackResult;
import com.grun.calorietracker.service.model.ProductNutritionOcrShadowRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductNutritionOcrShadowServiceImplTest {
    @Test
    void recordsAllVersionsMetricsAndNeverWritesGeminiIntoDisplayedFields() {
        ProductNutritionOcrFallbackService fallback = mock(ProductNutritionOcrFallbackService.class);
        ProductNutritionOcrShadowRunRepository repository = mock(ProductNutritionOcrShadowRunRepository.class);
        ProductNutritionOcrProperties properties = new ProductNutritionOcrProperties();
        properties.setEstimatedRequestCostUsd(0.004);
        ProductNutritionOcrFallbackRequest fallbackRequest = new ProductNutritionOcrFallbackRequest(
                10L, 20L, 30L, "nutrition-label-v4", 0.5, List.of("energy"), List.of(), true, "ai-v1");
        Map<String, Object> displayed = Map.of("energy", "190", "basis", "PER_100G");
        Map<String, Object> v3 = Map.of("energy", "180", "basis", "PER_SERVING");
        Map<String, Object> v4 = Map.of("energy", "200", "basis", "PER_100G");
        Map<String, Object> user = Map.of("energy", "200", "basis", "PER_100G");
        Map<String, Object> gemini = Map.of("energy", Map.of(
                "rawValue", "200", "unit", "kcal", "basis", "PER_100G",
                "evidenceBox", Map.of("x", 0.1, "y", 0.2, "width", 0.2, "height", 0.1)));
        when(fallback.analyzeIfNeeded(fallbackRequest)).thenReturn(Optional.of(
                new ProductNutritionOcrFallbackResult("GEMINI", "gemini-test", gemini)));
        ProductNutritionOcrShadowService service = new ProductNutritionOcrShadowServiceImpl(
                fallback, repository, properties, new ObjectMapper(), new ProductNutritionOcrReconcilerImpl());
        MDC.put("correlationId", "cid-shadow");
        try {
            var result = service.compare(new ProductNutritionOcrShadowRequest(
                    fallbackRequest, displayed, v3, v4, user));

            assertEquals(displayed, result.displayedFields());
            assertNotSame(result.displayedFields(), result.geminiFields());
            assertEquals(0, result.v3ExactMatchRate());
            assertEquals(1, result.v4ExactMatchRate());
            assertEquals(1, result.geminiExactMatchRate());
            assertTrue(result.v4BasisExact());
            assertTrue(result.geminiBasisExact());
            assertEquals(0.004, result.estimatedCostUsd());
            ArgumentCaptor<ProductNutritionOcrShadowRunEntity> saved =
                    ArgumentCaptor.forClass(ProductNutritionOcrShadowRunEntity.class);
            verify(repository).save(saved.capture());
            assertEquals(10L, saved.getValue().getReviewCaseId());
            assertEquals("cid-shadow", saved.getValue().getCorrelationId());
            assertTrue(saved.getValue().getGeminiFieldsJson().contains("rawValue"));
            assertTrue(saved.getValue().getReconciliationJson().contains("AGREED"));
        } finally {
            MDC.remove("correlationId");
        }
    }
}
