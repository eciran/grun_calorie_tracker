package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.PaymentProvider;
import com.grun.calorietracker.enums.SubscriptionPlan;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "subscription_credit_allocations", uniqueConstraints =
        @UniqueConstraint(name = "uk_subscription_credit_allocation", columnNames = {"provider", "user_id", "allocation_key"}))
@Data
@NoArgsConstructor
public class SubscriptionCreditAllocationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50)
    private PaymentProvider provider;
    @Column(name = "allocation_key", nullable = false, length = 64)
    private String allocationKey;
    @Enumerated(EnumType.STRING) @Column(name = "plan_type", nullable = false, length = 20)
    private SubscriptionPlan planType;
    @Column(name = "quota_amount", nullable = false)
    private Integer quotaAmount;
    @Column(name = "purchased_at", nullable = false)
    private Instant purchasedAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "transaction_id", length = 255)
    private String transactionId;
    @Column(name = "original_transaction_id", length = 255)
    private String originalTransactionId;
    @Column(name = "first_provider_event_id", nullable = false, length = 255)
    private String firstProviderEventId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
