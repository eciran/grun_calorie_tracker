package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.SubscriptionFeature;

public record RuntimeClientConfigDto(
        String releaseVersion,
        String minimumIosVersion,
        String minimumAndroidVersion,
        Boolean maintenanceEnabled,
        String maintenanceMessage,
        SubscriptionFeature rolloutFeature,
        Boolean rolloutEnabledForUser
) {
}
