package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "advanced_goal_save_requests", uniqueConstraints =
        @UniqueConstraint(name = "uk_advanced_goal_save_user_key", columnNames = {"user_id", "idempotency_key"}))
@Data
public class AdvancedGoalSaveRequestEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "goal_id", nullable = false)
    private UserGoalEntity goal;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
