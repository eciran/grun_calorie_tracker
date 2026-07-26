package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminOnboardingAnalyticsDto;

public interface AdminOnboardingAnalyticsService {
    AdminOnboardingAnalyticsDto getSummary(int hours);
}
