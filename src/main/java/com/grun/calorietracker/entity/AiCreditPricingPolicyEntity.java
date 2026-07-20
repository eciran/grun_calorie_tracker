package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.AiCreditPricingMode;
import com.grun.calorietracker.enums.SubscriptionFeature;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_credit_pricing_policies")
@Data
public class AiCreditPricingPolicyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 60)
    private SubscriptionFeature feature;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_mode", nullable = false, length = 40)
    private AiCreditPricingMode pricingMode;

    @Column(name = "base_credit_cost", nullable = false)
    private Integer baseCreditCost;

    @Column(name = "included_units", nullable = false)
    private Integer includedUnits;

    @Column(name = "units_per_additional_credit", nullable = false)
    private Integer unitsPerAdditionalCredit;

    @Column(name = "context_surcharge", nullable = false)
    private Integer contextSurcharge;

    @Column(name = "max_credit_cost", nullable = false)
    private Integer maxCreditCost;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
