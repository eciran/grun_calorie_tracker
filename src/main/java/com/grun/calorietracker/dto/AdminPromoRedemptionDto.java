package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.PromoRedemptionStatus;

import java.time.LocalDateTime;

public record AdminPromoRedemptionDto(
        Long id, Long promoId, Long userId, PromoRedemptionStatus status,
        String idempotencyKey, String providerEventId, Long amountMinor,
        String currency, String rejectionReason, LocalDateTime appliedAt,
        LocalDateTime convertedAt, String entitlementGuardrail
) {}
