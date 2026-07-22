package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminNotificationCampaignRequestDto {
    @NotBlank
    @Size(max = 160)
    private String name;

    @NotBlank
    @Size(max = 120)
    private String title;

    @NotBlank
    @Size(max = 1000)
    private String message;

    @NotNull
    private NotificationCampaignCategory category;

    @NotNull
    private NotificationCampaignChannel channel;

    @Size(max = 255)
    private String targetRoute;

    private SubscriptionPlan targetPlan;
    private MarketRegion targetRegion;
    private PreferredLanguage targetLanguage;
}