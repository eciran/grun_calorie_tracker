package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.BillingPeriod;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AdminSubscriptionUpdateRequestDto {
    @NotNull(message = "{validation.subscription.plan.required}")
    private SubscriptionPlan planType;

    @NotNull(message = "{validation.subscription.status.required}")
    private SubscriptionStatus status;

    @NotNull(message = "{validation.subscription.billing-period.required}")
    private BillingPeriod billingPeriod;

    private LocalDate startDate;
    private LocalDate endDate;

    @PositiveOrZero(message = "{validation.subscription.ai-quota.non-negative}")
    private Integer aiMonthlyQuota;

    @PositiveOrZero(message = "{validation.subscription.ai-usage.non-negative}")
    private Integer aiUsedThisPeriod;

    private Boolean autoRenew;
    private String provider;
    private String providerSubscriptionId;
}
