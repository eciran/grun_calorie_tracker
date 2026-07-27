package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.ProgressAnalyticsDto;
import com.grun.calorietracker.dto.ProgressBasicAnalyticsDto;

import java.time.LocalDate;

public interface ProgressAnalyticsService {
    ProgressAnalyticsDto getAnalytics(String email, LocalDate startDate, LocalDate endDate, boolean comparePrevious);

    ProgressBasicAnalyticsDto getBasicAnalytics(String email, LocalDate startDate, LocalDate endDate);
}
