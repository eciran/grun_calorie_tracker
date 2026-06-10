package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AchievementSummaryDto;

public interface AchievementService {
    AchievementSummaryDto getMyAchievements(String email);

    AchievementSummaryDto evaluateMyAchievements(String email);
}
