package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.MealReminderOccurrenceStatus;
import com.grun.calorietracker.service.reminder.MealReminderContract;
import com.grun.calorietracker.service.reminder.MealReminderDecision;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "meal_reminder_occurrences",
        uniqueConstraints = @UniqueConstraint(name = "uq_meal_reminder_occurrence_user_day_slot",
                columnNames = {"user_id", "local_date", "slot"}))
@Data
public class MealReminderOccurrenceEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private MealReminderContract.Slot slot;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private MealReminderDecision.Candidate candidate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private MealReminderContract.Message messageVariant;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private MealReminderOccurrenceStatus status;
    @Column(name = "policy_version", nullable = false, length = 80)
    private String policyVersion;
    @Column(name = "eligible_at", nullable = false)
    private Instant eligibleAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "reservation_active", nullable = false)
    private boolean reservationActive;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "notification_id")
    private NotificationEntity notification;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
