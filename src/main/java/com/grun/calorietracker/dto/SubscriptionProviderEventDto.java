package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.PaymentProvider;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Payment provider event stored for subscription synchronization audit.")
public class SubscriptionProviderEventDto {
    private Long id;
    private PaymentProvider provider;
    private String providerEventId;
    private String providerAppUserId;
    private String eventType;
    private String productId;
    private String entitlementIds;
    private String transactionId;
    private String originalTransactionId;
    private Long userId;
    private String userEmail;
    private SubscriptionProviderEventStatus status;
    private String processingError;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
}
