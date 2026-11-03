package com.grun.calorietracker.dto;

import java.util.List;

public record BrandedDuplicateRecommendationDto(
        String action,
        Integer confidence,
        Long suggestedSurvivorProductId,
        String reason,
        List<String> blockers
) {
}
