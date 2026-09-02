package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "meal_reminder_schedules")
@Data
public class MealReminderScheduleEntity {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(name = "next_evaluation_at", nullable = false)
    private Instant nextEvaluationAt;

    @Column(name = "lease_owner", length = 80)
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "last_duration_ms")
    private Long lastDurationMs;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
