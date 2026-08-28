package com.grun.calorietracker.service.model;

import java.util.List;
import java.util.Map;

public record ProductNutritionOcrFallbackRequest(
        Long reviewCaseId,
        Long nutritionAssetId,
        Long requesterUserId,
        String parserVersion,
        double localConfidence,
        List<String> uncertainFields,
        List<Map<String, Object>> wordBoxes,
        boolean aiProcessingAllowed,
        String aiProcessingConsentVersion
) {
    public ProductNutritionOcrFallbackRequest {
        if (reviewCaseId == null || nutritionAssetId == null || requesterUserId == null) {
            throw new IllegalArgumentException("Private review case, nutrition asset and requester are required.");
        }
        if (!Double.isFinite(localConfidence) || localConfidence < 0 || localConfidence > 1) {
            throw new IllegalArgumentException("Local confidence must be between 0 and 1.");
        }
        uncertainFields = uncertainFields == null ? List.of() : List.copyOf(uncertainFields);
        wordBoxes = wordBoxes == null ? List.of() : List.copyOf(wordBoxes);
        aiProcessingConsentVersion = aiProcessingConsentVersion == null ? null : aiProcessingConsentVersion.trim();
        if (aiProcessingAllowed && (aiProcessingConsentVersion == null || aiProcessingConsentVersion.isBlank())) {
            throw new IllegalArgumentException("AI nutrition label processing consent version is required.");
        }
    }
}
