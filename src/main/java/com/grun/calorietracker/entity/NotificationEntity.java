package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserEntity user;

    private String message;

    @Column(length = 2000)
    private String note;

    @Column(length = 64)
    private String primaryAction;

    private Integer actionAmountMl;

    @Column(length = 120)
    private String title;

    private String type; // "info", "warning", "reminder"

    private String severity; // "INFO", "WARNING", "CRITICAL"

    private String source;

    private String targetType;

    private String targetId;

    private String targetRoute;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private NotificationCampaignEntity campaign;

    @Column(name = "visible_in_app", nullable = false)
    private Boolean visibleInApp = true;

    private Boolean isRead;

    private LocalDateTime createdAt;
}
