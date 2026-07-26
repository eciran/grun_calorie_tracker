package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.NotificationCampaignCategory;
import com.grun.calorietracker.enums.NotificationCampaignChannel;
import com.grun.calorietracker.enums.NotificationCampaignStatus;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.SubscriptionPlan;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_campaigns")
@Data
public class NotificationCampaignEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationCampaignCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationCampaignChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationCampaignStatus status;

    @Column(name = "target_route", length = 255)
    private String targetRoute;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_plan", length = 32)
    private SubscriptionPlan targetPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_region", length = 32)
    private MarketRegion targetRegion;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_language", length = 16)
    private PreferredLanguage targetLanguage;

    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @Column(nullable = false, length = 255)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private Long lastProcessedUserId = 0L;

    @Column(nullable = false)
    private Long estimatedAudience = 0L;

    @Column(nullable = false)
    private Long processedCount = 0L;

    @Column(nullable = false)
    private Long inAppCount = 0L;

    @Column(nullable = false)
    private Long pushSentCount = 0L;

    @Column(nullable = false)
    private Long pushSkippedCount = 0L;

    @Column(nullable = false)
    private Long pushFailedCount = 0L;

    @Column(nullable = false)
    private Long openedCount = 0L;

    @Column(nullable = false)
    private Long clickedCount = 0L;

    @Column(nullable = false)
    private Long dismissedCount = 0L;

    @Column(nullable = false)
    private Long convertedCount = 0L;

    @Column(nullable = false)
    private Long suppressedCount = 0L;

    @Column(nullable = false)
    private Integer frequencyCapHours = 24;

    @Column(nullable = false)
    private Integer frequencyCapMax = 3;

    @Column(length = 2048)
    private String failureMessage;
}