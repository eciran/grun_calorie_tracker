package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.BillingPeriod;
import com.grun.calorietracker.enums.PaymentProvider;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Current user subscription and AI quota state.")
public class SubscriptionDto {
    private SubscriptionPlan planType;
    private SubscriptionStatus status;
    private BillingPeriod billingPeriod;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate quotaResetDate;
    private LocalDate aiAddonQuotaExpiresAt;
    private Integer aiMonthlyQuota;
    private Integer aiAddonQuota;
    private Integer aiTotalQuotaThisPeriod;
    private Integer aiUsedThisPeriod;
    private Integer aiBaseRemainingThisPeriod;
    private Integer aiAddonRemainingThisPeriod;
    private Integer aiRemainingThisPeriod;
    private Boolean activeEntitlement;
    private Boolean aiAccessAllowed;
    private Boolean upgradeRecommended;
    private Boolean autoRenew;
    private PaymentProvider provider;
    private String providerProductId;
}
