package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.service.UserGoalService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserGoalServiceImpl implements UserGoalService {

    private final GoalRepository userGoalRepository;

    public UserGoalServiceImpl(GoalRepository userGoalRepository) {
        this.userGoalRepository = userGoalRepository;
    }

    @Override
    public UserGoalEntity saveUserGoal(UserGoalEntity goalData){
        goalData.setCreatedAt(LocalDateTime.now());
        return userGoalRepository.save(goalData);
    }

    @Override
    public GoalCalculationResponse calculateGoal(UserGoalEntity goalData, UserEntity user) {
        double bmr = getBmrDetails(user);

        int goalCalories = getGoalCalories(goalData, user, bmr);
        
        int proteinGrams = (int) Math.round(goalCalories * goalData.getGoalType().getProteinPercentage() / 4);
        int fatGrams = (int) Math.round(goalCalories * goalData.getGoalType().getFatPercentage() / 9);

        int remainingCaloriesForCarbs = goalCalories - (proteinGrams * 4) - (fatGrams * 9);
        int carbGrams = remainingCaloriesForCarbs > 0 ? (int) Math.round(remainingCaloriesForCarbs / 4.0) : 0;

        return new GoalCalculationResponse(goalCalories, proteinGrams, fatGrams, carbGrams);
    }

    private static double getBmrDetails(UserEntity user) {
        double bmr;
        double leanBodyMassKg;

        if (user.getBodyFatPercentage() != null && user.getBodyFatPercentage() > 0) {

            leanBodyMassKg = user.getWeight() * (1 - (user.getBodyFatPercentage() / 100.0));
            bmr = 370 + (21.6 * leanBodyMassKg);
        } else {
            if ("MALE".equalsIgnoreCase(user.getGender())) {
                bmr = (10 * user.getWeight()) + (6.25 * user.getHeight()) - (5 * user.getAge()) + 5;
            } else {
                bmr = (10 * user.getWeight()) + (6.25 * user.getHeight()) - (5 * user.getAge()) - 161;
            }
        }
        return bmr;
    }

    private static int getGoalCalories(UserGoalEntity goalData, UserEntity user, double bmr) {
        double activityMultiplier = goalData.getActivityLevel().getMultiplier();
        int maintenanceCalories = (int) Math.round(bmr * activityMultiplier);

        int calorieAdjustment = 0;

        if (goalData.getWeeklyWeightChangeTargetKg() != null) {
            calorieAdjustment = (int) Math.round((goalData.getWeeklyWeightChangeTargetKg() * 7700) / 7.0);
        } else {
            calorieAdjustment = goalData.getGoalType().getCalorieAdjustment();
        }

        int goalCalories = maintenanceCalories + calorieAdjustment;

        if ("MALE".equalsIgnoreCase(user.getGender()) && goalCalories < 1500) {
            goalCalories = 1500;
        } else if ("FEMALE".equalsIgnoreCase(user.getGender()) && goalCalories < 1200) {
            goalCalories = 1200;
        }
        return goalCalories;
    }

    @Override
    public void deleteGoalByUser(UserEntity user) {
        Optional<UserGoalEntity> existingGoal = userGoalRepository.findByUser(user);
        existingGoal.ifPresent(userGoalRepository::delete);
    }



}
