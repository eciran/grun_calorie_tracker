package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminSubscriptionUpdateRequestDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.dto.SubscriptionFeatureAccessDto;
import com.grun.calorietracker.dto.SubscriptionPlanFeatureDto;
import com.grun.calorietracker.dto.SubscriptionProviderEventCommand;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.SubscriptionPlan;

import java.time.LocalDate;
import java.util.List;

public interface SubscriptionService {
    SubscriptionDto getCurrentSubscription(String email);
    SubscriptionDto getUserSubscriptionForAdmin(Long userId);
    SubscriptionDto updateUserSubscription(Long userId, AdminSubscriptionUpdateRequestDto request);
    SubscriptionFeatureAccessDto getFeatureAccess(String email);
    SubscriptionDto consumeAiQuota(String email);
    SubscriptionDto resetUserAiQuota(Long userId);
    SubscriptionDto grantAiAddonQuota(Long userId, int amount, int validityDays);
    SubscriptionDto refundConsumedAiQuota(Long userId, int amount);
    SubscriptionDto applyProviderEvent(Long userId, SubscriptionProviderEventCommand command);
    boolean hasFeatureAccess(String email, SubscriptionFeature feature);
    void assertFeatureAccess(String email, SubscriptionFeature feature);
    List<SubscriptionPlanFeatureDto> listPlanFeatures();
    SubscriptionPlanFeatureDto updatePlanFeature(SubscriptionPlan planType,
                                                 SubscriptionFeature feature,
                                                 boolean enabled,
                                                 LocalDate effectiveFrom);
}
