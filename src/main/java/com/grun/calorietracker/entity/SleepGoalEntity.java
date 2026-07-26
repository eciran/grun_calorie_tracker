package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "sleep_goals", uniqueConstraints = {
        @UniqueConstraint(name = "uq_sleep_goals_user", columnNames = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class SleepGoalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "target_minutes", nullable = false)
    private Integer targetMinutes;

    @Column(name = "preferred_bedtime")
    private LocalTime preferredBedtime;

    @Column(name = "preferred_wake_time")
    private LocalTime preferredWakeTime;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
