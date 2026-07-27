package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminDashboardGrowthDto;

import java.time.LocalDate;

public interface AdminGrowthAnalyticsService {

    AdminDashboardGrowthDto getGrowth(LocalDate from, LocalDate to, String timeZone);
}
