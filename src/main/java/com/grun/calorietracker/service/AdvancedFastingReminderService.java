package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdvancedFastingReminderSettingsDto;

public interface AdvancedFastingReminderService {
    int createDueReminderNotifications();
    AdvancedFastingReminderSettingsDto getSettings(String email);
    AdvancedFastingReminderSettingsDto updateSettings(String email, AdvancedFastingReminderSettingsDto request);
}