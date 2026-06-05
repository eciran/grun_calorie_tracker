package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.WaterDailySummaryDto;
import com.grun.calorietracker.dto.WaterLogDto;
import com.grun.calorietracker.dto.WaterLogRequestDto;
import com.grun.calorietracker.dto.WaterReminderSettingsDto;
import com.grun.calorietracker.dto.WaterReminderSettingsRequestDto;

import java.time.LocalDate;

public interface WaterTrackingService {
    WaterLogDto addWaterLog(String email, WaterLogRequestDto request);
    WaterDailySummaryDto getDailySummary(String email, LocalDate date);
    void deleteWaterLog(String email, Long id);
    WaterReminderSettingsDto getReminderSettings(String email);
    WaterReminderSettingsDto updateReminderSettings(String email, WaterReminderSettingsRequestDto request);
    int createDueReminderNotifications();
}
