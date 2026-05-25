package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.PaymentProvider;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "subscription_provider_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_subscription_provider_event", columnNames = {"provider", "provider_event_id"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionProviderEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentProvider provider;

    @Column(name = "provider_event_id", nullable = false, length = 255)
    private String providerEventId;

    @Column(name = "provider_app_user_id", length = 255)
    private String providerAppUserId;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "product_id", length = 255)
    private String productId;

    @Column(name = "entitlement_ids", length = 1000)
    private String entitlementIds;

    @Column(name = "transaction_id", length = 255)
    private String transactionId;

    @Column(name = "original_transaction_id", length = 255)
    private String originalTransactionId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SubscriptionProviderEventStatus status;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "processing_error", length = 1000)
    private String processingError;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    private LocalDateTime processedAt;
}
