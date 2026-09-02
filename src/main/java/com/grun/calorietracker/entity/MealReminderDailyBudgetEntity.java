package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "meal_reminder_daily_budgets",
        uniqueConstraints = @UniqueConstraint(name = "uq_meal_reminder_budget_user_day", columnNames = {"user_id", "local_date"}))
@Data
public class MealReminderDailyBudgetEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;
    @Column(name = "reserved_count", nullable = false)
    private int reservedCount;
    @Column(name = "catchup_count", nullable = false)
    private int catchupCount;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;
}
