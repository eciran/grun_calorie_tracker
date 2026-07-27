package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.EnergyBalanceAnalyticsDto;

import java.time.LocalDate;

public interface EnergyBalanceAnalyticsService {

    EnergyBalanceAnalyticsDto getAnalytics(String email, LocalDate startDate, LocalDate endDate);
}