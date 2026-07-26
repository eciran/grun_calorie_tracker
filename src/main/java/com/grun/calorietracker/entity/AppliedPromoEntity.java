package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.PromoRedemptionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "applied_promos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppliedPromoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "promo_code_id", nullable = false)
    private PromoCodeEntity promoCode;

    @Column(nullable = false)
    private LocalDateTime appliedAt;

    @Column(nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    @Column(length = 160)
    private String providerEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PromoRedemptionStatus status;

    private Long amountMinor;

    @Column(length = 3)
    private String currency;

    @Column(length = 500)
    private String rejectionReason;

    private LocalDateTime convertedAt;
}
