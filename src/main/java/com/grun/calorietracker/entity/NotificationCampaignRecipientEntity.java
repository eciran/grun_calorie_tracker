package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.NotificationCampaignRecipientStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_campaign_recipients",
        uniqueConstraints = @UniqueConstraint(name = "uq_notification_campaign_recipient", columnNames = {"campaign_id", "user_id"}))
@Data
public class NotificationCampaignRecipientEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private NotificationCampaignEntity campaign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id")
    private NotificationEntity notification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationCampaignRecipientStatus status;

    @Column(nullable = false)
    private Integer pushAttempted = 0;

    @Column(nullable = false)
    private Integer pushSent = 0;

    @Column(nullable = false)
    private Integer pushSkipped = 0;

    @Column(nullable = false)
    private Integer pushFailed = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;
    private LocalDateTime openedAt;
    private LocalDateTime clickedAt;
    private LocalDateTime dismissedAt;
    private LocalDateTime convertedAt;

    @Column(length = 160)
    private String suppressionReason;
}