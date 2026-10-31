package com.grun.calorietracker.dto;

import java.util.List;

public record FoodProductFamilyPreviewDto(
        boolean eligible,
        String familyKey,
        Long representativeProductId,
        List<Long> memberProductIds,
        List<String> errors,
        List<String> warnings
) {
}
