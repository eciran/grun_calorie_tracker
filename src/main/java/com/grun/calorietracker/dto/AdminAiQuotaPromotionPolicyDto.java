package com.grun.calorietracker.dto;

import java.time.Instant;

public record AdminAiQuotaPromotionPolicyDto(
        Long id,
        Long version,
        boolean enabled,
        int minimumIntervalHours,
        int maxImpressions24h,
        int dismissCooldownHours,
        int rolloutPercentage,
        long campaignVersion,
        String changeReason,
        String updatedBy,
        Instant updatedAt) { }
