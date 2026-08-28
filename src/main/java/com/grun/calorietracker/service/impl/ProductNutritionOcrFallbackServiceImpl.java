package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.ProductNutritionOcrProperties;
import com.grun.calorietracker.exception.AiProviderException;
import com.grun.calorietracker.service.ProductNutritionOcrFallbackService;
import com.grun.calorietracker.service.ProductNutritionOcrEvidenceResolver;
import com.grun.calorietracker.service.ProductNutritionOcrProvider;
import com.grun.calorietracker.service.ProductNutritionOcrResultCache;
import com.grun.calorietracker.service.model.ProductNutritionOcrEvidence;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackRequest;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class ProductNutritionOcrFallbackServiceImpl implements ProductNutritionOcrFallbackService {
    private static final String GEMINI = "GEMINI";

    private final ProductNutritionOcrProperties properties;
    private final List<ProductNutritionOcrProvider> providers;
    private final Optional<ProductNutritionOcrEvidenceResolver> evidenceResolver;
    private final ProductNutritionOcrResultCache resultCache;

    @Autowired
    public ProductNutritionOcrFallbackServiceImpl(
            ProductNutritionOcrProperties properties,
            List<ProductNutritionOcrProvider> providers,
            Optional<ProductNutritionOcrEvidenceResolver> evidenceResolver,
            ProductNutritionOcrResultCache resultCache) {
        this.properties = properties;
        this.providers = providers;
        this.evidenceResolver = evidenceResolver;
        this.resultCache = resultCache;
    }

    public ProductNutritionOcrFallbackServiceImpl(
            ProductNutritionOcrProperties properties,
            List<ProductNutritionOcrProvider> providers,
            ProductNutritionOcrEvidenceResolver evidenceResolver,
            ProductNutritionOcrResultCache resultCache) {
        this(properties, providers, Optional.of(evidenceResolver), resultCache);
    }

    @Override
    public Optional<ProductNutritionOcrFallbackResult> analyzeIfNeeded(ProductNutritionOcrFallbackRequest request) {
        if (!properties.isCloudEnabled() || !request.aiProcessingAllowed()
                || !rolloutAllows(request.requesterUserId())
                || request.localConfidence() >= properties.getFallbackThreshold()) {
            return Optional.empty();
        }
        ProductNutritionOcrProvider provider = providers.stream()
                .filter(candidate -> GEMINI.equalsIgnoreCase(candidate.providerId()))
                .findFirst()
                .orElseThrow(() -> new AiProviderException("Product nutrition OCR provider is not configured."));
        ProductNutritionOcrEvidenceResolver resolver = evidenceResolver.orElseThrow(() ->
                new AiProviderException("Private product nutrition OCR evidence storage is not configured."));
        ProductNutritionOcrEvidence evidence = resolver.resolve(request);
        Optional<ProductNutritionOcrFallbackResult> cached = resultCache.get(
                request, evidence, properties.getGeminiModel());
        if (cached.isPresent()) return cached;
        ProductNutritionOcrFallbackResult result = provider.analyze(request, evidence);
        result = targetedSecondPass(provider, request, evidence, result);
        resultCache.put(request, evidence, properties.getGeminiModel(), result);
        return Optional.of(result);
    }

    private boolean rolloutAllows(Long userId) {
        if (properties.getInternalUserIds().contains(userId)) return !"OFF".equals(properties.getRolloutStage());
        int bucket = Math.floorMod(Long.hashCode(userId), 100);
        return switch (properties.getRolloutStage()) {
            case "SMALL_PILOT" -> bucket < Math.min(10, properties.getRolloutPercentage());
            case "WIDE_PILOT" -> bucket < properties.getRolloutPercentage();
            default -> false;
        };
    }

    private ProductNutritionOcrFallbackResult targetedSecondPass(
            ProductNutritionOcrProvider provider,
            ProductNutritionOcrFallbackRequest original,
            ProductNutritionOcrEvidence evidence,
            ProductNutritionOcrFallbackResult first) {
        if (!properties.isTargetedSecondPassEnabled()) return first;
        String problemField = original.uncertainFields().stream()
                .filter(field -> missing(first.fields().get(field))).findFirst().orElse(null);
        if (problemField == null) return first;
        List<Map<String, Object>> cropWords = targetWords(problemField, original.wordBoxes());
        if (cropWords.isEmpty() || cropWords.size() >= original.wordBoxes().size()) return first;
        ProductNutritionOcrFallbackRequest targeted = new ProductNutritionOcrFallbackRequest(
                original.reviewCaseId(), original.nutritionAssetId(), original.requesterUserId(),
                original.parserVersion(), original.localConfidence(), List.of(problemField), cropWords,
                original.aiProcessingAllowed(), original.aiProcessingConsentVersion());
        ProductNutritionOcrFallbackResult second = provider.analyze(targeted, evidence);
        Object replacement = second.fields().get(problemField);
        if (missing(replacement)) return first;
        Map<String, Object> merged = new LinkedHashMap<>(first.fields());
        merged.put(problemField, replacement);
        return new ProductNutritionOcrFallbackResult(first.provider(), first.model(), Map.copyOf(merged));
    }

    private boolean missing(Object value) {
        if (value == null) return true;
        return value instanceof Map<?, ?> map && map.get("rawValue") == null;
    }

    private List<Map<String, Object>> targetWords(String field, List<Map<String, Object>> words) {
        String needle = switch (field.toLowerCase(Locale.ROOT)) {
            case "energy", "calories" -> "ener";
            case "carbohydrate", "carbs" -> "karbo";
            case "fiber", "fibre" -> "lif";
            default -> field.toLowerCase(Locale.ROOT);
        };
        Double anchorY = words.stream().filter(word -> text(word).contains(needle))
                .map(this::centerY).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (anchorY == null) return List.of();
        return words.stream().filter(word -> {
            Double y = centerY(word);
            return y != null && Math.abs(y - anchorY) <= 0.045;
        }).toList();
    }

    private String text(Map<String, Object> word) {
        Object value = word.get("text");
        return value == null ? "" : value.toString().toLowerCase(Locale.ROOT);
    }

    private Double centerY(Map<String, Object> word) {
        if (!(word.get("box") instanceof Map<?, ?> box)
                || !(box.get("y") instanceof Number y)
                || !(box.get("height") instanceof Number height)) return null;
        return y.doubleValue() + height.doubleValue() / 2;
    }
}
