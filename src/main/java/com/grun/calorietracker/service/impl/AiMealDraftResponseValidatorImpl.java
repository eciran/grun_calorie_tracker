package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AiMealDraftItemDto;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.AiMealDraftResponseValidator;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
public class AiMealDraftResponseValidatorImpl implements AiMealDraftResponseValidator {

    private static final int MAX_ITEMS = 20;

    @Override
    public AiMealDraftResponseDto validateAndNormalize(AiMealDraftResponseDto response,
                                                       AiRequestType expectedType,
                                                       AiProvider expectedProvider,
                                                       String expectedModel) {
        if (response == null) {
            throw new IllegalArgumentException("AI provider returned an empty response.");
        }
        response.setRequestType(expectedType);
        response.setProvider(expectedProvider);
        response.setModel(expectedModel);
        response.setStatus(AiRequestStatus.DRAFT_CREATED);
        if (response.getSuggestedLogDate() == null) {
            response.setSuggestedLogDate(LocalDateTime.now());
        }
        if (isBlank(response.getSuggestedMealType())) {
            response.setSuggestedMealType("SNACK");
        } else {
            response.setSuggestedMealType(response.getSuggestedMealType().trim().toUpperCase());
        }
        validateItems(response.getItems());
        normalizeQuality(response);
        return response;
    }

    private void validateItems(List<AiMealDraftItemDto> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("AI provider returned no meal draft items.");
        }
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("AI provider returned too many meal draft items.");
        }
        for (AiMealDraftItemDto item : items) {
            validateItem(item);
        }
    }

    private void validateItem(AiMealDraftItemDto item) {
        if (item == null || isBlank(item.getName())) {
            throw new IllegalArgumentException("AI provider returned an unnamed meal draft item.");
        }
        if (item.getQuantity() != null && item.getQuantity() <= 0) {
            throw new IllegalArgumentException("AI provider returned a non-positive meal draft quantity.");
        }
        if (item.getConfidence() != null && (item.getConfidence() < 0 || item.getConfidence() > 1)) {
            throw new IllegalArgumentException("AI provider returned confidence outside the 0-1 range.");
        }
        item.setName(normalizeFoodDisplayName(item.getName()));
        if (item.getUnit() != null) {
            item.setUnit(item.getUnit().trim());
        }
        if (item.getPortionEstimateMethod() == null || item.getPortionEstimateMethod().isBlank()) {
            item.setPortionEstimateMethod("UNKNOWN");
        }
        if (item.getNeedsUserPortionConfirmation() == null) {
            item.setNeedsUserPortionConfirmation(item.getQuantity() == null || item.getUnit() == null || item.getUnit().isBlank() || requiresReview(item));
        }
        if (isBlank(item.getReasoning())) {
            item.setReasoning("Estimated from the provided meal input and kept as an editable AI snapshot.");
        }
        if (isBlank(item.getPortionNote())) {
            item.setPortionNote(Boolean.TRUE.equals(item.getNeedsUserPortionConfirmation())
                    ? "Confirm the portion before saving; calories and macros depend on the final amount."
                    : "Portion appears usable, but you can adjust it before saving.");
        }
        item.setAlternativeMatchNames(normalizeAlternativeNames(item.getAlternativeMatchNames()));
    }

    private List<String> normalizeAlternativeNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return names.stream()
                .map(this::normalizeFoodDisplayName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String normalizeFoodDisplayName(String value) {
        return FoodProductNormalizationRules.normalizeProductDisplayName(value);
    }

    private void normalizeQuality(AiMealDraftResponseDto response) {
        response.setSchemaVersion("ai_response_v3");
        if (response.getReviewReasons() == null) {
            response.setReviewReasons(List.of());
        }
        if (response.getAssumptions() == null) {
            response.setAssumptions(List.of());
        }
        if (response.getNextBestActions() == null) {
            response.setNextBestActions(List.of());
        }
        if (isBlank(response.getResultType())) {
            response.setResultType("AI_SNAPSHOT");
        }
        if (isBlank(response.getUserMessage())) {
            response.setUserMessage("AI prepared an editable meal estimate. Review portions before adding it to your diary.");
        }
        if (isBlank(response.getProfessionalSummary())) {
            response.setProfessionalSummary(response.getSummary());
        }
        if (response.getConfidence() == null) {
            response.setConfidence(minConfidence(response.getItems()));
        }
        if (response.getQualityScore() == null && response.getConfidence() != null) {
            response.setQualityScore((int) Math.round(response.getConfidence() * 100));
        }
        if (response.getEstimatedUncertainty() == null || response.getEstimatedUncertainty().isBlank()) {
            response.setEstimatedUncertainty(resolveUncertainty(response.getConfidence()));
        }
    }

    private Double minConfidence(List<AiMealDraftItemDto> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream()
                .map(AiMealDraftItemDto::getConfidence)
                .filter(value -> value != null)
                .min(Double::compareTo)
                .orElse(null);
    }

    private String resolveUncertainty(Double confidence) {
        if (confidence == null || confidence < 0.55) {
            return "HIGH";
        }
        if (confidence < 0.8) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private boolean requiresReview(AiMealDraftItemDto item) {
        return Boolean.TRUE.equals(item.getReviewRequired()) || item.getConfidence() == null || item.getConfidence() < 0.75;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
