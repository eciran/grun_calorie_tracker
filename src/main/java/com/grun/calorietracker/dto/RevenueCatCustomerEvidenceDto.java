package com.grun.calorietracker.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RevenueCatCustomerEvidenceDto(
        Long userId,
        String environment,
        boolean providerReachable,
        String statusMessage,
        LocalDateTime checkedAt,
        SubscriptionDto backendSubscription,
        RevenueCatVerificationStateDto verification,
        List<RevenueCatPurchaseEvidenceDto> purchases) {
}
