package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FreePromotionPlacement;
import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Entity
@Table(name = "free_promotion_reservations")
@Data
public class FreePromotionReservationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Column(nullable = false, unique = true, length = 64) private String reservationToken;
    @Column(nullable = false, length = 100) private String sessionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private FreePromotionPlacement placement;
    @Column(nullable = false) private Long campaignVersion;
    @Column(nullable = false) private Instant reservedAt;
    @Column(nullable = false) private Instant expiresAt;
    private Instant impressionAt;
    private Instant dismissedAt;
    private Instant ctaAt;
}
