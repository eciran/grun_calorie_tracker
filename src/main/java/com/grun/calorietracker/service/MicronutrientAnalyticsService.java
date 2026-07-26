package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.MicronutrientAnalyticsDto;

import java.time.LocalDate;

public interface MicronutrientAnalyticsService {

    MicronutrientAnalyticsDto getAnalytics(
            String email,
            LocalDate startDate,
            LocalDate endDate,
            boolean comparePrevious
    );
}
