package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.dto.UserGoalDto;

public interface UserGoalService {
    public UserGoalDto saveUserGoal(UserGoalDto goal, String email);
    public GoalCalculationResponse calculateGoal(UserGoalDto goalData, String email);
    public void deleteGoalByUser(String email);
}
