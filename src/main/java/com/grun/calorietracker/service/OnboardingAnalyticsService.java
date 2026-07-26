package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.OnboardingAnalyticsEventRequestDto;
import com.grun.calorietracker.enums.OnboardingStep;
import com.grun.calorietracker.enums.ProductAnalyticsEventType;

public interface OnboardingAnalyticsService {
    void recordServerEvent(String email, ProductAnalyticsEventType eventType, OnboardingStep step);
    void recordClientEvent(String email, OnboardingAnalyticsEventRequestDto request);
}
