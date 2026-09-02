package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.NotificationEngagementType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "notification_engagements", uniqueConstraints = @UniqueConstraint(
        name = "uq_notification_engagement", columnNames = {"notification_id", "user_id", "engagement_type"}))
@Data
public class NotificationEngagementEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "notification_id", nullable = false)
    private NotificationEntity notification;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Enumerated(EnumType.STRING) @Column(name = "engagement_type", nullable = false, length = 24)
    private NotificationEngagementType engagementType;
    @Column(nullable = false, length = 24)
    private String source;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
