package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.SubscriptionPlan;

import java.time.Instant;
import java.util.List;

public record AiQuotaPromotionDecisionDto(
        boolean eligible,
        String reason,
        String reservationToken,
        Instant expiresAt,
        Long campaignVersion,
        SubscriptionPlan currentPlan,
        SubscriptionPlan upgradePlan,
        Integer remainingCredits,
        List<AiQuotaPromotionOfferDto> creditOffers) {

    public static AiQuotaPromotionDecisionDto denied(String reason, Long version) {
        return new AiQuotaPromotionDecisionDto(false, reason, null, null, version,
                null, null, null, List.of());
    }
}
