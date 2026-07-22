package com.grun.calorietracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminNotificationCampaignPreviewDto {
    private Long campaignId;
    private long estimatedAudience;
    private boolean marketingConsentRequired;
}