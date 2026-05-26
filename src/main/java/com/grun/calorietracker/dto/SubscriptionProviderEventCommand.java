package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.PaymentProvider;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionStatus;
import lombok.Data;

import java.time.LocalDate;

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
}
