package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Entity
@Table(name = "admin_sessions")
@Data
public class AdminSessionEntity {
    @Id
    @Column(length = 36)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Column(name = "session_token_hash", nullable = false, unique = true, length = 64)
    private String sessionTokenHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;
    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;
    @Column(name = "device_label", length = 200)
    private String deviceLabel;
    @Column(name = "masked_ip", length = 64)
    private String maskedIp;
    @Column(name = "revoked_at")
    private Instant revokedAt;
}
