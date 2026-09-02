package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.MealReminderOutboxStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "meal_reminder_outbox",
        uniqueConstraints = @UniqueConstraint(name = "uq_meal_reminder_outbox_occurrence", columnNames = "occurrence_id"))
@Data
public class MealReminderOutboxEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "occurrence_id", nullable = false)
    private MealReminderOccurrenceEntity occurrence;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "notification_id", nullable = false)
    private NotificationEntity notification;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private MealReminderOutboxStatus status;
    @Column(name = "available_at", nullable = false)
    private Instant availableAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "lease_owner", length = 80)
    private String leaseOwner;
    @Column(name = "lease_until")
    private Instant leaseUntil;
    @Column(name = "dispatch_count", nullable = false)
    private int dispatchCount;
    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
