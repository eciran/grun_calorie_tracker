package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdvancedGoalPreviewDto;
import com.grun.calorietracker.dto.AdvancedGoalRequestDto;
import com.grun.calorietracker.dto.UserGoalDto;

public interface AdvancedUserGoalService {
    AdvancedGoalPreviewDto preview(AdvancedGoalRequestDto request, String email);
    UserGoalDto save(AdvancedGoalRequestDto request, String email, String idempotencyKey);
    UserGoalDto restoreAutomatic(AdvancedGoalRequestDto request, String email);
}
