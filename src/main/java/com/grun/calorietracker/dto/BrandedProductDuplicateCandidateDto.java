package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One branded product record included in a potential duplicate identity group.")
public record BrandedProductDuplicateCandidateDto(
        Long productId,
        String productName,
        String barcode,
        String sourceKey,
        String marketRegion,
        String preparationState,
        Double servingSize,
        String servingUnit,
        Double calories,
        Double protein,
        Double carbs,
        Double fat,
        Integer qualityScore,
        String verificationStatus,
        String dataSource
) {
}
