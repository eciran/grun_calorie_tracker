package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.NotificationDeliveryAttemptStatus;
import com.grun.calorietracker.enums.NotificationDeliveryChannel;
import com.grun.calorietracker.enums.PushProvider;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "notification_delivery_attempts",
        uniqueConstraints = @UniqueConstraint(name = "uq_notification_attempt_outbox_token",
                columnNames = {"outbox_id", "push_token_id"}))
@Data
public class NotificationDeliveryAttemptEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outbox_id", nullable = false)
    private NotificationOutboxEntity outbox;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "push_token_id")
    private UserPushTokenEntity pushToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationDeliveryChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PushProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationDeliveryAttemptStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "provider_message_id", length = 512)
    private String providerMessageId;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "provider_accepted_at")
    private Instant providerAcceptedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
