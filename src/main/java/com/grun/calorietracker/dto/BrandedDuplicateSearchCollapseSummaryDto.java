package com.grun.calorietracker.dto;

public record BrandedDuplicateSearchCollapseSummaryDto(
        Long id,
        Long survivorProductId,
        Boolean active,
        String candidateFingerprint,
        String appliedBy,
        String appliedAt,
        String revertedBy,
        String revertedAt
) {
}
