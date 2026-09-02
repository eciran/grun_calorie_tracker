package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.MealReminderAttemptStatus;
import com.grun.calorietracker.enums.PushProvider;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "meal_reminder_delivery_attempts",
        uniqueConstraints = @UniqueConstraint(name = "uq_meal_reminder_attempt_outbox_token",
                columnNames = {"outbox_id", "push_token_id"}))
@Data
public class MealReminderDeliveryAttemptEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "outbox_id", nullable = false)
    private MealReminderOutboxEntity outbox;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "push_token_id", nullable = false)
    private UserPushTokenEntity pushToken;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PushProvider provider;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private MealReminderAttemptStatus status;
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
    @Column(name = "receipt_at")
    private Instant receiptAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
