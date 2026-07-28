package com.grun.calorietracker.dto;

import java.time.LocalDateTime;

public record AdvancedFastingGovernanceDto(
        String activeSafetyPolicyVersion,
        int maximumContinuousFastingHours,
        boolean reminderEnabled,
        int preStartMinutes,
        int nearingCompletionMinutes,
        int missedPlanMinutes,
        int maxRetryAttempts,
        long pendingDeliveries,
        long deferredDeliveries,
        long failedDeliveries,
        long suppressedDeliveries,
        long blockedUnsafeRequests,
        long schedulerFailures,
        long pushFailures,
        LocalDateTime configUpdatedAt
) {
}