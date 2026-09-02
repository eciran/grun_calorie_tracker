package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.NotificationCampaignChannel;
import com.grun.calorietracker.enums.PreferredLanguage;

public record AdminSubscriptionNotificationPreviewDto(
        String definitionKey,
        PreferredLanguage language,
        String title,
        String message,
        String severity,
        String targetRoute,
        NotificationCampaignChannel channel,
        boolean definitionEnabled) { }
