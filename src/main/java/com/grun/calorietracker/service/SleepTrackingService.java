package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.enums.HealthProvider;

import java.time.LocalDate;
import java.util.List;

public interface SleepTrackingService {
    SleepSessionDto createManual(String email, SleepSessionRequestDto request);
    SleepSessionDto syncProvider(String email, HealthProvider provider, SleepSessionRequestDto request);
    List<SleepSessionDto> list(String email, LocalDate startDate, LocalDate endDate);
    void deleteManual(String email, Long id);
    SleepGoalDto getGoal(String email);
    SleepGoalDto upsertGoal(String email, SleepGoalRequestDto request);
    SleepDailySummaryDto dailySummary(String email, LocalDate date);
    SleepWeeklySummaryDto weeklySummary(String email, LocalDate endDate);
}
