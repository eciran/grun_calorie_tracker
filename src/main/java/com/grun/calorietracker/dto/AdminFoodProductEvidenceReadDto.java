package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodProductReviewAssetType;

import java.time.Instant;

public record AdminFoodProductEvidenceReadDto(
        Long assetId,
        FoodProductReviewAssetType assetType,
        String contentType,
        String signedUrl,
        Instant expiresAt
) {
}
