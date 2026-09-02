package com.grun.calorietracker.dto;

import java.time.Instant;

public record AdminSubscriptionNotificationPolicyDto(
        Long version,
        boolean requestedDeliveryEnabled,
        boolean emergencyStopped,
        String stopReason,
        boolean deploymentDeliveryEnabled,
        String releaseStage,
        int testAccountCount,
        int pilotAccountCount,
        int livePercentage,
        boolean waterProducerMigrated,
        boolean stepProducerMigrated,
        boolean basicFastingProducerMigrated,
        boolean pushProviderEnabled,
        boolean effectiveDeliveryEnabled,
        String effectiveReason,
        String updatedBy,
        Instant updatedAt) { }
