package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.dto.GoalCalculationRequestDto;
import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.dto.UserProfileDto;
import java.time.LocalDate;
import java.util.List;

public interface UserGoalService {
    UserGoalDto saveUserGoal(GoalCalculationRequestDto goal, String email);
    GoalCalculationResponse calculateGoal(GoalCalculationRequestDto goalData, String email);
    GoalCalculationResponse calculateGoalPreview(GoalCalculationRequestDto goalData, UserProfileDto profile);
    UserGoalDto getCurrentUserGoal(String email);
    UserGoalDto getGoalForDate(String email, LocalDate date);
    List<UserGoalDto> getGoalHistory(String email);
    void deleteGoalByUser(String email);
}
