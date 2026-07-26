package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.dto.GoalCalculationRequestDto;
import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.dto.UserProfileDto;

public interface UserGoalService {
    UserGoalDto saveUserGoal(GoalCalculationRequestDto goal, String email);
    GoalCalculationResponse calculateGoal(GoalCalculationRequestDto goalData, String email);
    GoalCalculationResponse calculateGoalPreview(GoalCalculationRequestDto goalData, UserProfileDto profile);
    UserGoalDto getCurrentUserGoal(String email);
    void deleteGoalByUser(String email);
}
