package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.PaymentProvider;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.Instant;
import com.grun.calorietracker.enums.RevenueCatEventType;

@Data
public class SubscriptionProviderEventCommand {
    private PaymentProvider provider;
    private String providerCustomerId;
    private String providerProductId;
    private String providerEventId;
    private String providerSubscriptionId;
    private String providerTransactionId;
    private String providerOriginalTransactionId;
    private SubscriptionPlan planType;
    private SubscriptionStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean autoRenew;
    private Integer aiAddonQuotaAmount;
    private Integer aiAddonValidityDays;
    private Boolean refund;
    private RevenueCatEventType eventType;
    private Instant providerEventAt;
    private Instant purchasedAt;
    private Instant expirationAt;
    private Boolean grantPlanCreditAllocation;
    private String creditAllocationKey;
}
