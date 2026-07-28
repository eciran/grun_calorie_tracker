package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.dto.GoalCalculationRequestDto;
import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.enums.AnalyticsMutationSource;
import com.grun.calorietracker.enums.GoalType;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.mapper.UserGoalMapper;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.service.UserGoalService;
import com.grun.calorietracker.service.UserService;
import com.grun.calorietracker.service.UserAnalyticsCacheRevisionService;
import com.grun.calorietracker.service.support.ProfileEnergyEstimate;
import com.grun.calorietracker.service.support.ProfileEnergyExpenditureCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserGoalServiceImpl implements UserGoalService {

    private static final double WEIGHT_DIRECTION_TOLERANCE_KG = 0.5;
    private static final double MAINTENANCE_TOLERANCE_KG = 1.0;
    private static final double KCAL_PER_KG = 7700.0;
    private static final int MALE_MINIMUM_CALORIES = 1500;
    private static final int FEMALE_MINIMUM_CALORIES = 1200;

    private final GoalRepository userGoalRepository;
    private final UserService userService;
    private final ProfileEnergyExpenditureCalculator profileEnergyCalculator;
    private final UserAnalyticsCacheRevisionService analyticsCacheRevisionService;

    @Override
    @Transactional
    public UserGoalDto saveUserGoal(GoalCalculationRequestDto goalData, String email) {
        log.info("Saving new goal for user: {}", email);

        UserEntity user = userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));

        // Calculate and validate before deleting the current goal. An invalid replacement
        // must never leave the user without their previously active goal.
        UserGoalDto calculatedGoal = buildCalculatedGoalDto(goalData, user);

        userGoalRepository.findByUser(user).ifPresent(existing -> {
            log.info("Deleting existing goal for user: {}", email);
            userGoalRepository.delete(existing);
        });

        UserGoalEntity newGoal = UserGoalMapper.toEntity(calculatedGoal, user);
        UserGoalEntity saved = userGoalRepository.save(newGoal);

        log.info("New goal saved for user: {} with id {}", email, saved.getId());
        analyticsCacheRevisionService.bump(user.getId(), AnalyticsMutationSource.GOAL);
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

    @Override
    public GoalCalculationResponse calculateGoalPreview(GoalCalculationRequestDto goalData, UserProfileDto profile) {
        if (profile == null
                || profile.getAge() == null
                || profile.getGender() == null
                || profile.getHeight() == null
                || profile.getWeight() == null) {
            throw new IllegalArgumentException("Profile metrics are required for goal preview");
        }
        UserEntity calculationProfile = new UserEntity();
        calculationProfile.setAge(profile.getAge());
        calculationProfile.setGender(profile.getGender());
        calculationProfile.setHeight(profile.getHeight());
        calculationProfile.setWeight(profile.getWeight());
        calculationProfile.setBodyFatPercentage(profile.getBodyFat());
        return calculateGoal(goalData, calculationProfile);
    }

    private UserGoalDto buildCalculatedGoalDto(GoalCalculationRequestDto goalData, UserEntity user) {
        GoalCalculationResponse calculation = calculateGoal(goalData, user);

        UserGoalDto calculatedGoal = new UserGoalDto();
        calculatedGoal.setTargetWeight(goalData.getTargetWeight());
        calculatedGoal.setDailyCalorieGoal(calculation.getCalculatedCalorieNeed());
        calculatedGoal.setDailyProteinGoal((double) calculation.getRecommendedProteinGrams());
        calculatedGoal.setDailyFatGoal((double) calculation.getRecommendedFatGrams());
        calculatedGoal.setDailyCarbGoal((double) calculation.getRecommendedCarbGrams());
        calculatedGoal.setWeeklyWeightChangeTargetKg(calculation.getEffectiveWeeklyRateKg());
        calculatedGoal.setGoalType(goalData.getGoalType());
        calculatedGoal.setActivityLevel(goalData.getActivityLevel());
        calculatedGoal.setCreatedAt(LocalDateTime.now());
        return calculatedGoal;
    }

    private GoalCalculationResponse calculateGoal(GoalCalculationRequestDto goalData, UserEntity user) {
        validateRequiredInputs(goalData, user);
        validateGoalDirection(goalData, user.getWeight());

        ProfileEnergyEstimate energy = profileEnergyCalculator.calculate(user, goalData.getActivityLevel())
                .orElseThrow(() -> new IllegalArgumentException("Complete profile metrics are required for energy calculation."));
        int maintenanceCalories = (int) Math.round(energy.totalDailyEnergyCalories());
        RateResult rate = resolveSafeWeeklyRate(goalData, user.getWeight());
        int calorieAdjustment = (int) Math.round((rate.effectiveRateKg() * KCAL_PER_KG) / 7.0);
        int minimumCalories = "MALE".equalsIgnoreCase(user.getGender())
                ? MALE_MINIMUM_CALORIES
                : FEMALE_MINIMUM_CALORIES;
        int rawGoalCalories = maintenanceCalories + calorieAdjustment;
        int goalCalories = Math.max(rawGoalCalories, minimumCalories);

        boolean floorApplied = rawGoalCalories < minimumCalories;
        String safetyWarning = appendWarning(
                rate.warning(),
                floorApplied ? "Daily calorie target was raised to the minimum safety floor." : null
        );

        GoalCalculationResponse response = calculateMacros(goalCalories, goalData.getGoalType());
        response.setFormula(energy.formula());
        response.setBmr(roundTwoDecimals(energy.restingEnergyCalories()));
        response.setMaintenanceCalories(maintenanceCalories);
        response.setRequestedWeeklyRateKg(goalData.getWeeklyWeightChangeTargetKg());
        int appliedCalorieAdjustment = goalCalories - maintenanceCalories;
        double effectiveRateAfterFloor = (appliedCalorieAdjustment * 7.0) / KCAL_PER_KG;
        double roundedEffectiveRate = roundThreeDecimals(effectiveRateAfterFloor);
        response.setEffectiveWeeklyRateKg(roundedEffectiveRate);
        response.setEstimatedDurationWeeks(estimateDurationWeeks(
                user.getWeight(),
                goalData.getTargetWeight(),
                roundedEffectiveRate
        ));
        response.setCalorieAdjustment(appliedCalorieAdjustment);
        response.setMinimumCalorieFloor(minimumCalories);
        response.setSafetyAdjusted(rate.adjusted() || floorApplied);
        response.setSafetyWarning(safetyWarning);
        return response;
    }

    private static GoalCalculationResponse calculateMacros(int goalCalories, GoalType goalType) {
        int proteinGrams = (int) Math.round(goalCalories * goalType.getProteinPercentage() / 4);
        int fatGrams = (int) Math.round(goalCalories * goalType.getFatPercentage() / 9);
        int remainingCaloriesForCarbs = goalCalories - (proteinGrams * 4) - (fatGrams * 9);
        int carbGrams = remainingCaloriesForCarbs > 0 ? (int) Math.round(remainingCaloriesForCarbs / 4.0) : 0;
        return new GoalCalculationResponse(goalCalories, proteinGrams, fatGrams, carbGrams);
    }

    private static RateResult resolveSafeWeeklyRate(GoalCalculationRequestDto goalData, double currentWeightKg) {
        GoalType goalType = goalData.getGoalType();
        if (goalType == GoalType.MAINTAIN_WEIGHT) {
            boolean adjusted = goalData.getWeeklyWeightChangeTargetKg() != null
                    && Math.abs(goalData.getWeeklyWeightChangeTargetKg()) > 0.0001;
            return new RateResult(
                    0.0,
                    adjusted,
                    adjusted ? "Weekly weight change was set to zero for a maintenance goal." : null
            );
        }

        double requestedMagnitude;
        if (goalData.getWeeklyWeightChangeTargetKg() == null) {
            requestedMagnitude = Math.abs(goalType.getCalorieAdjustment() * 7.0 / KCAL_PER_KG);
        } else {
            if (!Double.isFinite(goalData.getWeeklyWeightChangeTargetKg())
                    || Math.abs(goalData.getWeeklyWeightChangeTargetKg()) < 0.01) {
                throw new IllegalArgumentException("Weekly weight change must be greater than zero for this goal type.");
            }
            requestedMagnitude = Math.abs(goalData.getWeeklyWeightChangeTargetKg());
        }

        double maximumMagnitude = maximumSafeWeeklyRate(goalType, currentWeightKg);
        double effectiveMagnitude = Math.min(requestedMagnitude, maximumMagnitude);
        boolean adjusted = requestedMagnitude > maximumMagnitude + 0.0001;
        double direction = goalType == GoalType.LOSE_WEIGHT ? -1.0 : 1.0;
        return new RateResult(
                direction * effectiveMagnitude,
                adjusted,
                adjusted
                        ? "Requested weekly rate exceeded the safety limit and was reduced to "
                        + roundThreeDecimals(maximumMagnitude) + " kg/week."
                        : null
        );
    }

    private static double maximumSafeWeeklyRate(GoalType goalType, double currentWeightKg) {
        return switch (goalType) {
            case LOSE_WEIGHT -> Math.min(1.0, currentWeightKg * 0.01);
            case GAIN_WEIGHT -> Math.min(0.5, currentWeightKg * 0.005);
            case BUILD_MUSCLE -> Math.min(0.25, currentWeightKg * 0.003);
            case MAINTAIN_WEIGHT -> 0.0;
        };
    }

    private static void validateRequiredInputs(GoalCalculationRequestDto goalData, UserEntity user) {
        if (goalData == null
                || goalData.getTargetWeight() == null
                || goalData.getGoalType() == null
                || goalData.getActivityLevel() == null) {
            throw new IllegalArgumentException("Target weight, goal type, and activity level are required.");
        }
        if (user == null
                || user.getAge() == null
                || user.getGender() == null
                || user.getHeight() == null
                || user.getWeight() == null) {
            throw new IllegalArgumentException("Complete profile metrics are required for goal calculation.");
        }
        if (!Double.isFinite(goalData.getTargetWeight()) || goalData.getTargetWeight() < 30 || goalData.getTargetWeight() > 300) {
            throw new IllegalArgumentException("Target weight must be between 30 and 300 kg.");
        }
    }

    private static void validateGoalDirection(GoalCalculationRequestDto goalData, double currentWeightKg) {
        double difference = goalData.getTargetWeight() - currentWeightKg;
        switch (goalData.getGoalType()) {
            case LOSE_WEIGHT -> {
                if (difference >= -WEIGHT_DIRECTION_TOLERANCE_KG) {
                    throw new IllegalArgumentException("A weight-loss target must be lower than the current weight.");
                }
            }
            case GAIN_WEIGHT -> {
                if (difference <= WEIGHT_DIRECTION_TOLERANCE_KG) {
                    throw new IllegalArgumentException("A weight-gain target must be higher than the current weight.");
                }
            }
            case BUILD_MUSCLE -> {
                if (difference < -MAINTENANCE_TOLERANCE_KG) {
                    throw new IllegalArgumentException("A muscle-building target cannot be materially lower than the current weight.");
                }
            }
            case MAINTAIN_WEIGHT -> {
                if (Math.abs(difference) > MAINTENANCE_TOLERANCE_KG) {
                    throw new IllegalArgumentException("A maintenance target must stay within 1 kg of the current weight.");
                }
            }
        }
    }

    private static Integer estimateDurationWeeks(
            double currentWeightKg,
            double targetWeightKg,
            double effectiveWeeklyRateKg
    ) {
        double remainingKg = Math.abs(targetWeightKg - currentWeightKg);
        if (remainingKg < 0.01) {
            return 0;
        }
        double weeklyMagnitude = Math.abs(effectiveWeeklyRateKg);
        if (weeklyMagnitude < 0.01) {
            return null;
        }
        return (int) Math.ceil(remainingKg / weeklyMagnitude);
    }
    private static String appendWarning(String current, String additional) {
        if (additional == null || additional.isBlank()) {
            return current;
        }
        return current == null || current.isBlank() ? additional : current + " " + additional;
    }

    private static double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double roundThreeDecimals(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    @Override
    public UserGoalDto getCurrentUserGoal(String email) {
        UserEntity user = userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        return userGoalRepository.findByUser(user)
                .map(UserGoalMapper::toDto)
                .orElse(null);
    }

    @Override
    @Transactional
    public void deleteGoalByUser(String email) {
        UserEntity user = userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        if (user.getEmail() == null) {
            throw new InvalidCredentialsException("Invalid credential");
        }
        Optional<UserGoalEntity> existingGoal = userGoalRepository.findByUser(user);
        existingGoal.ifPresent(userGoalRepository::delete);
        if (existingGoal.isPresent()) {
            analyticsCacheRevisionService.bump(user.getId(), AnalyticsMutationSource.GOAL);
        }
    }

    private record RateResult(double effectiveRateKg, boolean adjusted, String warning) {
    }
}
