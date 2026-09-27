package com.grun.calorietracker.dto;

import java.time.Instant;

public record RevenueCatPurchaseEvidenceDto(
        String transactionId,
        String productId,
        String environment,
        String store,
        Instant purchasedAt,
        Instant expiresAt,
        boolean ownershipConflict) {
}
