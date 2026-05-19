package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.dto.GoalCalculationRequestDto;
import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.mapper.UserGoalMapper;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.service.UserGoalService;
import com.grun.calorietracker.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserGoalServiceImpl implements UserGoalService {

    private final GoalRepository userGoalRepository;
    private final UserService userService;

    @Override
    public UserGoalDto saveUserGoal(GoalCalculationRequestDto goalData, String email) {
        log.info("Saving new goal for user: {}", email);

        UserEntity user = userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));

        userGoalRepository.findByUser(user).ifPresent(existing -> {
            log.info("Deleting existing goal for user: {}", email);
            userGoalRepository.delete(existing);
        });

        UserGoalDto calculatedGoal = buildCalculatedGoalDto(goalData, user);
        UserGoalEntity newGoal = UserGoalMapper.toEntity(calculatedGoal, user);
        UserGoalEntity saved = userGoalRepository.save(newGoal);

        log.info("New goal saved for user: {} with id {}", email, saved.getId());

        return UserGoalMapper.toDto(saved);
    }


    @Override
    public GoalCalculationResponse calculateGoal(GoalCalculationRequestDto goalData, String email) {
        UserEntity user = userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));

        if (user.getEmail() == null) {
            throw new InvalidCredentialsException("Invalid credential");
        }

        return calculateGoal(goalData, user);
    }

    private static UserGoalDto buildCalculatedGoalDto(GoalCalculationRequestDto goalData, UserEntity user) {
        GoalCalculationResponse macros = calculateGoal(goalData, user);

        UserGoalDto calculatedGoal = new UserGoalDto();
        calculatedGoal.setTargetWeight(goalData.getTargetWeight());
        calculatedGoal.setDailyCalorieGoal(macros.getCalculatedCalorieNeed());
        calculatedGoal.setDailyProteinGoal((double) macros.getRecommendedProteinGrams());
        calculatedGoal.setDailyFatGoal((double) macros.getRecommendedFatGrams());
        calculatedGoal.setDailyCarbGoal((double) macros.getRecommendedCarbGrams());
        calculatedGoal.setWeeklyWeightChangeTargetKg(goalData.getWeeklyWeightChangeTargetKg() == null
                ? null
                : normalizedWeeklyWeightChangeKg(goalData));
        calculatedGoal.setGoalType(goalData.getGoalType());
        calculatedGoal.setActivityLevel(goalData.getActivityLevel());
        calculatedGoal.setCreatedAt(LocalDateTime.now());
        return calculatedGoal;
    }

    private static GoalCalculationResponse calculateGoal(GoalCalculationRequestDto goalData, UserEntity user) {
        double bmr = getBmrDetails(user);
        int goalCalories = getGoalCalories(goalData, user, bmr);
        return calculateMacros(goalCalories, goalData);
    }

    private static GoalCalculationResponse calculateMacros(int goalCalories, GoalCalculationRequestDto goalData) {
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

    private static int getGoalCalories(GoalCalculationRequestDto goalData, UserEntity user, double bmr) {
        double activityMultiplier = goalData.getActivityLevel().getMultiplier();
        int maintenanceCalories = (int) Math.round(bmr * activityMultiplier);

        int calorieAdjustment = 0;

        if (goalData.getWeeklyWeightChangeTargetKg() != null) {
            calorieAdjustment = (int) Math.round((normalizedWeeklyWeightChangeKg(goalData) * 7700) / 7.0);
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

    private static double normalizedWeeklyWeightChangeKg(GoalCalculationRequestDto goalData) {
        if (goalData.getWeeklyWeightChangeTargetKg() == null) {
            return 0.0;
        }

        double weeklyChange = Math.abs(goalData.getWeeklyWeightChangeTargetKg());
        return switch (goalData.getGoalType()) {
            case LOSE_WEIGHT -> -weeklyChange;
            case GAIN_WEIGHT, BUILD_MUSCLE -> weeklyChange;
            case MAINTAIN_WEIGHT -> 0.0;
        };
    }

    @Override
    public void deleteGoalByUser(String email) {
        UserEntity user = userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));

        if (user.getEmail() == null) {
            throw new InvalidCredentialsException("Invalid credential");
        }
        Optional<UserGoalEntity> existingGoal = userGoalRepository.findByUser(user);
        existingGoal.ifPresent(userGoalRepository::delete);
    }
}

