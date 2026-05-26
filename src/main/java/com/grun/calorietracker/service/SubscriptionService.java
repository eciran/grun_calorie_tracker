package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminSubscriptionUpdateRequestDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.dto.SubscriptionFeatureAccessDto;
import com.grun.calorietracker.dto.SubscriptionProviderEventCommand;
import com.grun.calorietracker.enums.SubscriptionFeature;

public interface SubscriptionService {
    SubscriptionDto getCurrentSubscription(String email);
    SubscriptionDto updateUserSubscription(Long userId, AdminSubscriptionUpdateRequestDto request);
    SubscriptionFeatureAccessDto getFeatureAccess(String email);
    SubscriptionDto consumeAiQuota(String email);
    SubscriptionDto resetUserAiQuota(Long userId);
    SubscriptionDto grantAiAddonQuota(Long userId, int amount, int validityDays);
    SubscriptionDto applyProviderEvent(Long userId, SubscriptionProviderEventCommand command);
    boolean hasFeatureAccess(String email, SubscriptionFeature feature);
    void assertFeatureAccess(String email, SubscriptionFeature feature);
}
