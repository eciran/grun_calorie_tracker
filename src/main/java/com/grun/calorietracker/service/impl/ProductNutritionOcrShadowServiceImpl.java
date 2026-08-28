package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.ProductNutritionOcrProperties;
import com.grun.calorietracker.entity.ProductNutritionOcrShadowRunEntity;
import com.grun.calorietracker.repository.ProductNutritionOcrShadowRunRepository;
import com.grun.calorietracker.service.ProductNutritionOcrFallbackService;
import com.grun.calorietracker.service.ProductNutritionOcrShadowService;
import com.grun.calorietracker.service.ProductNutritionOcrReconciler;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackResult;
import com.grun.calorietracker.service.model.ProductNutritionOcrShadowRequest;
import com.grun.calorietracker.service.model.ProductNutritionOcrShadowResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductNutritionOcrShadowServiceImpl implements ProductNutritionOcrShadowService {
    private final ProductNutritionOcrFallbackService fallbackService;
    private final ProductNutritionOcrShadowRunRepository repository;
    private final ProductNutritionOcrProperties properties;
    private final ObjectMapper objectMapper;
    private final ProductNutritionOcrReconciler reconciler;

    @Override
    public ProductNutritionOcrShadowResult compare(ProductNutritionOcrShadowRequest request) {
        long startedAt = System.nanoTime();
        Optional<ProductNutritionOcrFallbackResult> fallback = fallbackService.analyzeIfNeeded(request.fallbackRequest());
        Map<String, Object> gemini = fallback.map(ProductNutritionOcrFallbackResult::fields).orElseGet(Map::of);
        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
        ProductNutritionOcrShadowResult result = new ProductNutritionOcrShadowResult(
                request.displayedFields(), gemini, fallback.isPresent(),
                exactRate(request.v3Fields(), request.userConfirmedFields()),
                exactRate(request.v4Fields(), request.userConfirmedFields()),
                exactRate(gemini, request.userConfirmedFields()),
                basisExact(request.v3Fields(), request.userConfirmedFields()),
                basisExact(request.v4Fields(), request.userConfirmedFields()),
                basisExact(gemini, request.userConfirmedFields()),
                latencyMs, fallback.isPresent() ? properties.getEstimatedRequestCostUsd() : 0,
                reconciler.reconcile(request.v4Fields(), gemini));
        repository.save(entity(request, fallback.orElse(null), result));
        return result;
    }

    private ProductNutritionOcrShadowRunEntity entity(ProductNutritionOcrShadowRequest request,
                                                       ProductNutritionOcrFallbackResult fallback,
                                                       ProductNutritionOcrShadowResult result) {
        ProductNutritionOcrShadowRunEntity entity = new ProductNutritionOcrShadowRunEntity();
        entity.setReviewCaseId(request.fallbackRequest().reviewCaseId());
        entity.setRequesterUserId(request.fallbackRequest().requesterUserId());
        entity.setCorrelationId(MDC.get("correlationId"));
        entity.setParserVersion(request.fallbackRequest().parserVersion());
        entity.setModel(fallback == null ? null : fallback.model());
        entity.setFallbackInvoked(result.fallbackInvoked());
        entity.setV3FieldsJson(json(request.v3Fields())); entity.setV4FieldsJson(json(request.v4Fields()));
        entity.setGeminiFieldsJson(json(result.geminiFields())); entity.setUserFieldsJson(json(request.userConfirmedFields()));
        entity.setV3ExactMatchRate(result.v3ExactMatchRate()); entity.setV4ExactMatchRate(result.v4ExactMatchRate());
        entity.setGeminiExactMatchRate(result.geminiExactMatchRate()); entity.setV3BasisExact(result.v3BasisExact());
        entity.setV4BasisExact(result.v4BasisExact()); entity.setGeminiBasisExact(result.geminiBasisExact());
        entity.setLatencyMs(result.latencyMs()); entity.setEstimatedCostUsd(result.estimatedCostUsd());
        entity.setReconciliationJson(json(result.reconciliation()));
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    private double exactRate(Map<String, Object> candidate, Map<String, Object> truth) {
        if (truth.isEmpty()) return 0;
        var comparable = truth.entrySet().stream().filter(entry -> !"basis".equals(entry.getKey())).toList();
        if (comparable.isEmpty()) return 0;
        long matches = comparable.stream().filter(entry -> normalized(entry.getValue())
                .equals(normalized(candidate.get(entry.getKey())))).count();
        return (double) matches / comparable.size();
    }

    private boolean basisExact(Map<String, Object> candidate, Map<String, Object> truth) {
        Object expected = basisValue(truth);
        return expected != null && normalized(expected).equals(normalized(basisValue(candidate)));
    }

    private Object basisValue(Map<String, Object> fields) {
        if (fields.get("basis") != null) return fields.get("basis");
        return fields.values().stream().filter(Map.class::isInstance).map(Map.class::cast)
                .map(value -> value.get("basis")).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private String normalized(Object value) {
        if (value instanceof Map<?, ?> map && map.containsKey("rawValue")) value = map.get("rawValue");
        return Objects.toString(value, "").trim().replace(',', '.').toLowerCase(Locale.ROOT);
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("OCR shadow snapshot could not be encoded.", exception); }
    }
}
