package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.WaterDailySummaryDto;
import com.grun.calorietracker.dto.WaterGoalDto;
import com.grun.calorietracker.dto.WaterGoalRequestDto;
import com.grun.calorietracker.dto.WaterLogDto;
import com.grun.calorietracker.dto.WaterLogRequestDto;
import com.grun.calorietracker.dto.WaterRangeSummaryDto;
import com.grun.calorietracker.dto.WaterReminderSettingsDto;
import com.grun.calorietracker.dto.WaterReminderSettingsRequestDto;

import java.time.LocalDate;

public interface WaterTrackingService {
    WaterLogDto addWaterLog(String email, WaterLogRequestDto request);
    WaterLogDto updateWaterLog(String email, Long id, WaterLogRequestDto request);
    WaterDailySummaryDto getDailySummary(String email, LocalDate date);
    WaterRangeSummaryDto getRangeSummary(String email, LocalDate startDate, LocalDate endDate);
    WaterGoalDto getGoal(String email);
    WaterGoalDto updateGoal(String email, WaterGoalRequestDto request);
    void deleteWaterLog(String email, Long id);
    WaterReminderSettingsDto getReminderSettings(String email);
    WaterReminderSettingsDto updateReminderSettings(String email, WaterReminderSettingsRequestDto request);
    int createDueReminderNotifications();
}
