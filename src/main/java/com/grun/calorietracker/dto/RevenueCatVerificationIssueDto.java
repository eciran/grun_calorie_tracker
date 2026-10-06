package com.grun.calorietracker.dto;

import com.grun.calorietracker.entity.SubscriptionVerificationEntity;
import java.time.Instant;

public record RevenueCatVerificationIssueDto(Long userId, String status, String productId,
        String resolvedProductId, String reasonCode, int attempts, Instant createdAt,
        Instant updatedAt, Instant nextAttemptAt, boolean allocationReferencePresent) {
    public static RevenueCatVerificationIssueDto from(SubscriptionVerificationEntity state) {
        return new RevenueCatVerificationIssueDto(state.getUserId(), state.getStatus(), state.getProductId(),
                state.getResolvedProductId(), state.getReasonCode(), state.getAttempts(), state.getCreatedAt(),
                state.getUpdatedAt(), state.getNextAttemptAt(), state.getAllocationReference() != null);
    }
}
