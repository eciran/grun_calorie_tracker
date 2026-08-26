package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "advanced_goal_previews")
@Data
public class AdvancedGoalPreviewEntity {
    @Id @Column(length = 64)
    private String token;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    @Column(name = "profile_version", nullable = false, length = 64)
    private String profileVersion;
    @Column(name = "goal_version")
    private Long goalVersion;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
