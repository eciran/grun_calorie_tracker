package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.dto.GoalCalculationRequestDto;
import com.grun.calorietracker.dto.UserGoalDto;

public interface UserGoalService {
    UserGoalDto saveUserGoal(GoalCalculationRequestDto goal, String email);
    GoalCalculationResponse calculateGoal(GoalCalculationRequestDto goalData, String email);
    UserGoalDto getCurrentUserGoal(String email);
    void deleteGoalByUser(String email);
}
