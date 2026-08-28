package com.grun.calorietracker.service;

import com.grun.calorietracker.config.ProductNutritionOcrProperties;
import com.grun.calorietracker.service.impl.ProductNutritionOcrFallbackServiceImpl;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackRequest;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class ProductNutritionOcrFallbackServiceImplTest {
    @Test
    void closedCloudFlagNeverCallsProviderEvenForLowConfidence() {
        ProductNutritionOcrProperties properties = new ProductNutritionOcrProperties();
        ProductNutritionOcrProvider provider = mock(ProductNutritionOcrProvider.class);
        ProductNutritionOcrEvidenceResolver resolver = mock(ProductNutritionOcrEvidenceResolver.class);
        ProductNutritionOcrResultCache cache = mock(ProductNutritionOcrResultCache.class);
        ProductNutritionOcrFallbackService service = new ProductNutritionOcrFallbackServiceImpl(
                properties, List.of(provider), resolver, cache);
        ProductNutritionOcrFallbackRequest request = request(0.2);

        assertTrue(service.analyzeIfNeeded(request).isEmpty());
        verify(resolver, never()).resolve(request);
        verify(provider, never()).analyze(org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rolloutDefaultsOffAndInternalStageAllowsOnlyConfiguredUsers() {
        ProductNutritionOcrProperties properties = new ProductNutritionOcrProperties();
        properties.setCloudEnabled(true);
        ProductNutritionOcrProvider provider = mock(ProductNutritionOcrProvider.class);
        ProductNutritionOcrEvidenceResolver resolver = mock(ProductNutritionOcrEvidenceResolver.class);
        ProductNutritionOcrResultCache cache = mock(ProductNutritionOcrResultCache.class);
        ProductNutritionOcrFallbackService service = new ProductNutritionOcrFallbackServiceImpl(
                properties, List.of(provider), resolver, cache);

        assertTrue(service.analyzeIfNeeded(request(0.2)).isEmpty());
        properties.setRolloutStage("INTERNAL");
        properties.setInternalUserIds(java.util.Set.of(99L));
        assertTrue(service.analyzeIfNeeded(request(0.2)).isEmpty());
        verify(resolver, never()).resolve(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enabledFallbackCallsOnlyDedicatedGeminiProviderBelowThreshold() {
        ProductNutritionOcrProperties properties = new ProductNutritionOcrProperties();
        properties.setCloudEnabled(true);
        enableWidePilot(properties);
        ProductNutritionOcrProvider provider = mock(ProductNutritionOcrProvider.class);
        ProductNutritionOcrEvidenceResolver resolver = mock(ProductNutritionOcrEvidenceResolver.class);
        ProductNutritionOcrResultCache cache = mock(ProductNutritionOcrResultCache.class);
        var evidence = new com.grun.calorietracker.service.model.ProductNutritionOcrEvidence(
                new byte[]{1, 2, 3}, "image/jpeg", "abc");
        when(resolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(evidence);
        when(provider.providerId()).thenReturn("GEMINI");
        ProductNutritionOcrFallbackResult result = new ProductNutritionOcrFallbackResult(
                "GEMINI", properties.getGeminiModel(), Map.of());
        ProductNutritionOcrFallbackRequest request = request(0.6);
        when(provider.analyze(request, evidence)).thenReturn(result);
        ProductNutritionOcrFallbackService service = new ProductNutritionOcrFallbackServiceImpl(
                properties, List.of(provider), resolver, cache);

        assertEquals(result, service.analyzeIfNeeded(request).orElseThrow());
        verify(provider).analyze(request, evidence);
        verify(cache).put(request, evidence, properties.getGeminiModel(), result);
    }

    @Test
    void cacheHitReturnsPrivateResultWithoutProviderCall() {
        ProductNutritionOcrProperties properties = new ProductNutritionOcrProperties();
        properties.setCloudEnabled(true);
        enableWidePilot(properties);
        ProductNutritionOcrProvider provider = mock(ProductNutritionOcrProvider.class);
        when(provider.providerId()).thenReturn("GEMINI");
        ProductNutritionOcrEvidenceResolver resolver = mock(ProductNutritionOcrEvidenceResolver.class);
        ProductNutritionOcrResultCache cache = mock(ProductNutritionOcrResultCache.class);
        ProductNutritionOcrFallbackRequest request = request(0.6);
        var evidence = new com.grun.calorietracker.service.model.ProductNutritionOcrEvidence(
                new byte[]{1}, "image/jpeg", "abc");
        ProductNutritionOcrFallbackResult cached = new ProductNutritionOcrFallbackResult(
                "GEMINI", properties.getGeminiModel(), Map.of("energy", 200));
        when(resolver.resolve(request)).thenReturn(evidence);
        when(cache.get(request, evidence, properties.getGeminiModel())).thenReturn(java.util.Optional.of(cached));
        ProductNutritionOcrFallbackService service = new ProductNutritionOcrFallbackServiceImpl(
                properties, List.of(provider), resolver, cache);

        assertEquals(cached, service.analyzeIfNeeded(request).orElseThrow());
        verify(provider, never()).analyze(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingOptionalAiConsentKeepsLocalPathAndNeverReadsOrCallsProvider() {
        ProductNutritionOcrProperties properties = new ProductNutritionOcrProperties();
        properties.setCloudEnabled(true);
        enableWidePilot(properties);
        ProductNutritionOcrProvider provider = mock(ProductNutritionOcrProvider.class);
        ProductNutritionOcrEvidenceResolver resolver = mock(ProductNutritionOcrEvidenceResolver.class);
        ProductNutritionOcrResultCache cache = mock(ProductNutritionOcrResultCache.class);
        ProductNutritionOcrFallbackRequest request = new ProductNutritionOcrFallbackRequest(
                10L, 20L, 30L, "nutrition-label-v4", 0.2, List.of("energy"), List.of(), false, null);
        ProductNutritionOcrFallbackService service = new ProductNutritionOcrFallbackServiceImpl(
                properties, List.of(provider), resolver, cache);

        assertTrue(service.analyzeIfNeeded(request).isEmpty());
        verify(resolver, never()).resolve(request);
        verify(provider, never()).analyze(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void optionalSecondPassTargetsOneMissingFieldAndOneRowAtMostOnce() {
        ProductNutritionOcrProperties properties = new ProductNutritionOcrProperties();
        properties.setCloudEnabled(true);
        enableWidePilot(properties);
        properties.setTargetedSecondPassEnabled(true);
        ProductNutritionOcrProvider provider = mock(ProductNutritionOcrProvider.class);
        when(provider.providerId()).thenReturn("GEMINI");
        ProductNutritionOcrEvidenceResolver resolver = mock(ProductNutritionOcrEvidenceResolver.class);
        ProductNutritionOcrResultCache cache = mock(ProductNutritionOcrResultCache.class);
        var evidence = new com.grun.calorietracker.service.model.ProductNutritionOcrEvidence(
                new byte[]{1}, "image/jpeg", "abc");
        List<Map<String, Object>> words = List.of(
                word("Protein", 0.20), word("6", 0.21), word("Fat", 0.55), word("8", 0.56));
        ProductNutritionOcrFallbackRequest request = new ProductNutritionOcrFallbackRequest(
                10L, 20L, 30L, "nutrition-label-v4", 0.5, List.of("protein"), words, true, "ai-v1");
        when(resolver.resolve(request)).thenReturn(evidence);
        Map<String, Object> unreadable = new java.util.LinkedHashMap<>();
        unreadable.put("rawValue", null);
        ProductNutritionOcrFallbackResult first = new ProductNutritionOcrFallbackResult(
                "GEMINI", properties.getGeminiModel(), Map.of("protein", unreadable));
        ProductNutritionOcrFallbackResult second = new ProductNutritionOcrFallbackResult(
                "GEMINI", properties.getGeminiModel(), Map.of("protein", Map.of("rawValue", "6")));
        when(provider.analyze(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(evidence)))
                .thenReturn(first, second);
        ProductNutritionOcrFallbackService service = new ProductNutritionOcrFallbackServiceImpl(
                properties, List.of(provider), resolver, cache);

        assertEquals("6", ((Map<?, ?>) service.analyzeIfNeeded(request).orElseThrow()
                .fields().get("protein")).get("rawValue"));
        ArgumentCaptor<ProductNutritionOcrFallbackRequest> calls =
                ArgumentCaptor.forClass(ProductNutritionOcrFallbackRequest.class);
        verify(provider, org.mockito.Mockito.times(2)).analyze(calls.capture(), org.mockito.ArgumentMatchers.eq(evidence));
        ProductNutritionOcrFallbackRequest targeted = calls.getAllValues().get(1);
        assertEquals(List.of("protein"), targeted.uncertainFields());
        assertEquals(2, targeted.wordBoxes().size());
    }

    private ProductNutritionOcrFallbackRequest request(double confidence) {
        return new ProductNutritionOcrFallbackRequest(10L, 20L, 30L, "nutrition-label-v4", confidence,
                List.of("energy"), List.of(), true, "ai-v1");
    }

    private Map<String, Object> word(String text, double y) {
        return Map.of("text", text, "box", Map.of("x", 0.1, "y", y, "width", 0.2, "height", 0.02));
    }

    private void enableWidePilot(ProductNutritionOcrProperties properties) {
        properties.setRolloutStage("WIDE_PILOT");
        properties.setRolloutPercentage(100);
    }
}
