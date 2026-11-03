package com.grun.calorietracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "ai_quota_promotion_reservations")
@Data
public class AiQuotaPromotionReservationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Column(nullable = false, unique = true, length = 64) private String reservationToken;
    @Column(nullable = false, length = 100) private String sessionId;
    @Column(nullable = false) private Long campaignVersion;
    @Column(nullable = false) private Instant reservedAt;
    @Column(nullable = false) private Instant expiresAt;
    private Instant impressionAt;
    private Instant dismissedAt;
    private Instant ctaAt;
}
