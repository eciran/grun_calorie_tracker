package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.DailySummaryDto;

import java.time.LocalDate;

// Service interface for dashboard operations
public interface DashboardService {

    DailySummaryDto getDailySummary(String email, LocalDate date);
}