package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodProductReviewAssetType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record FoodProductUploadSessionDto(
        String sessionId,
        LocalDateTime expiresAt,
        List<Slot> slots
) {
    public record Slot(
            Long assetId,
            FoodProductReviewAssetType assetType,
            String uploadUrl,
            String method,
            Map<String, String> requiredHeaders,
            Instant uploadUrlExpiresAt
    ) {
    }
}
