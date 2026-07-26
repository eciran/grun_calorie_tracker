package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionStatus;

public record PromoProviderRedemptionCommand(
        Long userId,
        String providerEventId,
        String productId,
        String offeringId,
        String store,
        Long amountMinor,
        String currency,
        SubscriptionPlan previousPlan,
        SubscriptionStatus previousStatus
) {
}
