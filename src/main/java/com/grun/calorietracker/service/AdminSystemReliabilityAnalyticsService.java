package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminSystemReliabilityAnalyticsDto;

public interface AdminSystemReliabilityAnalyticsService {
    AdminSystemReliabilityAnalyticsDto getAnalytics(int windowHours);
}