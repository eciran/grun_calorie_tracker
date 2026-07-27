package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.PromoRedemptionStatus;

import java.time.LocalDateTime;

public record AdminPromoRedemptionRowDto(
        Long id, Long promoId, String promoCode,
        Long userId, String maskedUserEmail,
        PromoRedemptionStatus status,
        String providerEventReference,
        Long amountMinor, String currency,
        String rejectionReason, int duplicateHits,
        LocalDateTime appliedAt, LocalDateTime convertedAt, LocalDateTime lastDuplicateAt
) {
}
