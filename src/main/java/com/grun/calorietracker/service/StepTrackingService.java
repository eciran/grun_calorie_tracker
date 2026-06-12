package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.StepDailySummaryDto;
import com.grun.calorietracker.dto.StepGoalDto;
import com.grun.calorietracker.dto.StepGoalRequestDto;
import com.grun.calorietracker.dto.StepManualLogRequestDto;
import com.grun.calorietracker.dto.StepManualLogResponseDto;
import com.grun.calorietracker.dto.StepRangeSummaryDto;

import java.time.LocalDate;

public interface StepTrackingService {
    StepGoalDto getGoal(String email);
    StepGoalDto updateGoal(String email, StepGoalRequestDto request);
    StepDailySummaryDto getDailySummary(String email, LocalDate date);
    StepRangeSummaryDto getRangeSummary(String email, LocalDate startDate, LocalDate endDate);
    StepManualLogResponseDto addManualLog(String email, StepManualLogRequestDto request);
    int createDueReminderNotifications();
}
