package com.grun.calorietracker.dto;

import java.time.Instant;

public record FreePromotionDecisionDto(boolean eligible, String reason, String reservationToken,
        Instant expiresAt, Long campaignVersion) {
    public static FreePromotionDecisionDto denied(String reason, Long version) {
        return new FreePromotionDecisionDto(false, reason, null, null, version);
    }
}
