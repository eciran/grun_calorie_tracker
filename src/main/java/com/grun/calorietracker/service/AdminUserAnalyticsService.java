package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminUserAnalyticsDto;

import java.time.LocalDate;

public interface AdminUserAnalyticsService {

    AdminUserAnalyticsDto getAnalytics(LocalDate from, LocalDate to, String timeZone);
}
