package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.BillingPeriod;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    private SubscriptionPlan planType;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;

    @Enumerated(EnumType.STRING)
    private BillingPeriod billingPeriod;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer aiMonthlyQuota;

    private Integer aiAddonQuota;

    private LocalDate aiAddonQuotaExpiresAt;

    private Integer aiUsedThisPeriod;

    private LocalDate aiQuotaPeriodStartDate;

    private LocalDate aiQuotaPeriodEndDate;

    private Boolean autoRenew;

    private String provider;

    private String providerSubscriptionId;

    private LocalDateTime updatedAt;
}
