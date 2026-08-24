package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodProductAssetUploadState;
import com.grun.calorietracker.enums.FoodProductReviewAssetType;
import com.grun.calorietracker.enums.FoodProductUploadSessionStatus;

import java.time.LocalDateTime;
import java.util.List;

public record FoodProductUploadFinalizeDto(
        String sessionId,
        FoodProductUploadSessionStatus status,
        LocalDateTime finalizedAt,
        List<Asset> assets
) {
    public record Asset(
            Long assetId,
            FoodProductReviewAssetType assetType,
            FoodProductAssetUploadState uploadState,
            int width,
            int height
    ) {
    }
}
