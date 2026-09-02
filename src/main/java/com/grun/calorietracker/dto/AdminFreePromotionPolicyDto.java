package com.grun.calorietracker.dto;

import java.time.Instant;

public record AdminFreePromotionPolicyDto(Long id, Long version, boolean enabled, int minimumIntervalHours,
        int maxImpressions24h, int dismissCooldownHours, int minimumSessionNumber, int rolloutPercentage,
        long campaignVersion, String changeReason, String updatedBy, Instant updatedAt) { }
