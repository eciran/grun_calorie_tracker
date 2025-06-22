package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;

public interface UserGoalService {
    public UserGoalEntity saveUserGoal(UserGoalEntity goal);
    public GoalCalculationResponse calculateGoal(UserGoalEntity goalData, UserEntity user);
    public void deleteGoalByUser(UserEntity user);
}
