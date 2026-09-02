package com.grun.calorietracker.entity;

import com.grun.calorietracker.service.reminder.MealReminderContract;
import com.grun.calorietracker.service.reminder.MealReminderDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "meal_reminder_dry_run_decisions")
@Getter
@Setter
@NoArgsConstructor
public class MealReminderDryRunDecisionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_ref", nullable = false, length = 64)
    private String subjectRef;

    @Column(name = "policy_version", nullable = false, length = 80)
    private String policyVersion;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "local_date")
    private LocalDate localDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MealReminderDecision.Candidate candidate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MealReminderContract.Slot slot;

    @Column(name = "should_send", nullable = false)
    private boolean shouldSend;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MealReminderContract.Reason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "kcal_reason", length = 40)
    private MealReminderContract.KcalReason kcalReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_variant", length = 40)
    private MealReminderContract.Message messageVariant;
}
