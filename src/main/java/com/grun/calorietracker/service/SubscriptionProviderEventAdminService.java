package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.RevenueCatWebhookResponseDto;
import com.grun.calorietracker.dto.SubscriptionProviderEventDetailDto;
import com.grun.calorietracker.dto.SubscriptionProviderEventPageDto;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;

public interface SubscriptionProviderEventAdminService {
    SubscriptionProviderEventPageDto getEvents(SubscriptionProviderEventStatus status, String eventType, String productId, Long userId, int page, int size);
    SubscriptionProviderEventPageDto getUserHistory(Long userId, int page, int size);
    SubscriptionProviderEventDetailDto getEvent(Long id);
    RevenueCatWebhookResponseDto retryEvent(Long id);
}
