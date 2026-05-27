package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.SubscriptionPlan;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_subscription_entitlements")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSubscriptionEntitlementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "subscription_id")
    private SubscriptionEntity subscription;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private SubscriptionFeature feature;

    @Column(nullable = false)
    private Boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionPlan sourcePlan;

    @Column(nullable = false)
    private LocalDate validFrom;

    private LocalDate validUntil;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
