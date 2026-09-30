package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.BrandedDuplicateDecision;

public record BrandedProductDuplicateDecisionDto(
        Long id,
        String brandKey,
        String nameKey,
        BrandedDuplicateDecision decision,
        Long survivorProductId,
        String candidateFingerprint,
        String reason,
        String reviewedBy,
        String reviewedAt,
        Long version
) {
}
