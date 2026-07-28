package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdvancedFastingAnalyticsDto;

import java.time.LocalDate;

public interface AdvancedFastingAnalyticsService {
    AdvancedFastingAnalyticsDto getAnalytics(String email, LocalDate startDate, LocalDate endDate);
}