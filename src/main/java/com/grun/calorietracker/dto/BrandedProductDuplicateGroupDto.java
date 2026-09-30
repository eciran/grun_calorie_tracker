package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Potential branded duplicate group sharing a normalized brand and product name.")
public record BrandedProductDuplicateGroupDto(
        String brandKey,
        String nameKey,
        String brandName,
        String representativeName,
        Integer productCount,
        Integer barcodeCount,
        Integer missingBarcodeCount,
        Integer marketCount,
        Integer preparationCount,
        Integer servingCount,
        Integer missingServingCount,
        Integer nutritionCount,
        Integer missingNutritionCount,
        String decision,
        Boolean variantSignal,
        String candidateFingerprint,
        BrandedProductDuplicateDecisionDto storedDecision,
        Boolean decisionStale,
        BrandedDuplicateSearchCollapseSummaryDto searchCollapse,
        List<BrandedProductDuplicateCandidateDto> candidates
) {
}
