package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "promo_codes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromoCodeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 600)
    private String description;

    @Column(nullable = false)
    private Double discountPercent;

    private Integer maxUsageCount;

    @Column(nullable = false)
    private Integer usedCount = 0;

    private LocalDate expirationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PromoStatus status = PromoStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PromoType promoType = PromoType.CAMPAIGN;

    @Column(nullable = false)
    private boolean active;

    private LocalDateTime startAt;
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private SubscriptionPlan targetPlan;

    @Column(length = 120)
    private String targetProductId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PromoStore targetStore = PromoStore.ALL;

    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private MarketRegion targetRegion;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PromoEligibilityRule eligibilityRule = PromoEligibilityRule.ALL_USERS;

    @Column(nullable = false)
    private Integer perUserLimit = 1;

    private Integer globalLimit;

    @Column(length = 120)
    private String campaignKey;

    @Column(length = 160)
    private String providerOfferId;

    @Column(length = 160)
    private String providerProductId;

    @Column(nullable = false, length = 255)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, length = 255)
    private String updatedBy;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(length = 500)
    private String deactivatedReason;
}
