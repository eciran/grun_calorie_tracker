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
@Table(name = "subscription_plan_features",
        uniqueConstraints = @UniqueConstraint(name = "uk_subscription_plan_feature", columnNames = {"plan_type", "feature"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanFeatureEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 30)
    private SubscriptionPlan planType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private SubscriptionFeature feature;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDateTime updatedAt;
}
