package com.grun.calorietracker.dto;

import java.time.Instant;

public record RevenueCatVerificationStateDto(
        String status,
        String productId,
        int attempts,
        Instant nextAttemptAt,
        Instant leaseUntil,
        Instant updatedAt,
        boolean allocationReferencePresent) {
}
