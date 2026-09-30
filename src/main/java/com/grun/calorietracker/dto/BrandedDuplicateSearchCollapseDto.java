package com.grun.calorietracker.dto;

import java.util.List;

public record BrandedDuplicateSearchCollapseDto(
        Long id,
        Long decisionId,
        String brandKey,
        String nameKey,
        Long survivorProductId,
        List<Long> suppressedProductIds,
        String candidateFingerprint,
        String reason,
        String appliedBy,
        String appliedAt,
        boolean active,
        String revertedBy,
        String revertedAt,
        String revertReason,
        Long version
) {
}
