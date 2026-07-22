package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminNotificationCampaignDto {
    private Long id;
    private String name;
    private String title;
    private String message;
    private NotificationCampaignCategory category;
    private NotificationCampaignChannel channel;
    private NotificationCampaignStatus status;
    private String targetRoute;
    private SubscriptionPlan targetPlan;
    private MarketRegion targetRegion;
    private PreferredLanguage targetLanguage;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long estimatedAudience;
    private Long processedCount;
    private Long inAppCount;
    private Long pushSentCount;
    private Long pushSkippedCount;
    private Long pushFailedCount;
    private String failureMessage;
}