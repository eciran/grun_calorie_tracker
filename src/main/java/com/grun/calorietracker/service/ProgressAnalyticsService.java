package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.ProgressAnalyticsDto;

import java.time.LocalDate;

public interface ProgressAnalyticsService {
    ProgressAnalyticsDto getAnalytics(String email, LocalDate startDate, LocalDate endDate, boolean comparePrevious);
}
