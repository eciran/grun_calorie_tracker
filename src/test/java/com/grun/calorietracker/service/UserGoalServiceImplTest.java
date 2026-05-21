package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.dto.GoalCalculationRequestDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.GoalType;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.service.impl.UserGoalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserGoalServiceImplTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private UserService userService;

    private UserGoalServiceImpl userGoalService;

    @BeforeEach
    void setUp() {
        userGoalService = new UserGoalServiceImpl(goalRepository, userService);
    }

    @Test
    void calculateGoal_maleMaintainSedentary_usesMifflinStJeorAndActivityMultiplier() {
        UserEntity user = user("user@example.com", "MALE", 30, 180.0, 80.0, null);
        GoalCalculationRequestDto goal = goal(GoalType.MAINTAIN_WEIGHT, ActivityLevel.SEDENTARY, null);
        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        GoalCalculationResponse result = userGoalService.calculateGoal(goal, "user@example.com");

        assertEquals(2136, result.getCalculatedCalorieNeed());
        assertEquals(107, result.getRecommendedProteinGrams());
        assertEquals(71, result.getRecommendedFatGrams());
        assertEquals(267, result.getRecommendedCarbGrams());
    }

    @Test
    void calculateGoal_femaleLoseModerate_usesDefaultDeficitWhenWeeklyTargetIsMissing() {
        UserEntity user = user("user@example.com", "FEMALE", 35, 165.0, 65.0, null);
        GoalCalculationRequestDto goal = goal(GoalType.LOSE_WEIGHT, ActivityLevel.MODERATE, null);
        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        GoalCalculationResponse result = userGoalService.calculateGoal(goal, "user@example.com");

        assertEquals(1585, result.getCalculatedCalorieNeed());
        assertEquals(99, result.getRecommendedProteinGrams());
        assertEquals(53, result.getRecommendedFatGrams());
        assertEquals(178, result.getRecommendedCarbGrams());
    }

    @Test
    void calculateGoal_loseWeightPositiveWeeklyTarget_isTreatedAsDeficit() {
        UserEntity user = user("user@example.com", "MALE", 30, 180.0, 80.0, null);
        GoalCalculationRequestDto goal = goal(GoalType.LOSE_WEIGHT, ActivityLevel.MODERATE, 0.5);
        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        GoalCalculationResponse result = userGoalService.calculateGoal(goal, "user@example.com");

        assertEquals(2209, result.getCalculatedCalorieNeed());
    }

    @Test
    void calculateGoal_loseWeightNegativeWeeklyTarget_keepsSameDeficitAsPositiveInput() {
        UserEntity user = user("user@example.com", "MALE", 30, 180.0, 80.0, null);
        GoalCalculationRequestDto goal = goal(GoalType.LOSE_WEIGHT, ActivityLevel.MODERATE, -0.5);
        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        GoalCalculationResponse result = userGoalService.calculateGoal(goal, "user@example.com");

        assertEquals(2209, result.getCalculatedCalorieNeed());
    }

    @Test
    void calculateGoal_whenBodyFatExists_usesKatchMcardleFormula() {
        UserEntity user = user("user@example.com", "MALE", 30, 180.0, 80.0, 20.0);
        GoalCalculationRequestDto goal = goal(GoalType.MAINTAIN_WEIGHT, ActivityLevel.SEDENTARY, null);
        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        GoalCalculationResponse result = userGoalService.calculateGoal(goal, "user@example.com");

        assertEquals(2103, result.getCalculatedCalorieNeed());
    }

    @Test
    void saveUserGoal_persistsCalculatedCaloriesAndMacros() {
        UserEntity user = user("user@example.com", "MALE", 30, 180.0, 80.0, null);
        GoalCalculationRequestDto goal = goal(GoalType.LOSE_WEIGHT, ActivityLevel.MODERATE, 0.5);
        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(goalRepository.findByUser(user)).thenReturn(Optional.empty());
        when(goalRepository.save(any(UserGoalEntity.class))).thenAnswer(invocation -> {
            UserGoalEntity saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        var result = userGoalService.saveUserGoal(goal, "user@example.com");

        assertEquals(2209, result.getDailyCalorieGoal());
        assertEquals(138.0, result.getDailyProteinGoal());
        assertEquals(74.0, result.getDailyFatGoal());
        assertEquals(248.0, result.getDailyCarbGoal());
        assertEquals(-0.5, result.getWeeklyWeightChangeTargetKg());
    }

    @Test
    void getCurrentUserGoal_whenGoalExists_returnsSavedGoal() {
        UserEntity user = user("user@example.com", "MALE", 30, 180.0, 80.0, null);
        UserGoalEntity goal = new UserGoalEntity();
        goal.setDailyCalorieGoal(2209);
        goal.setDailyProteinGoal(138.0);
        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(goalRepository.findByUser(user)).thenReturn(Optional.of(goal));

        var result = userGoalService.getCurrentUserGoal("user@example.com");

        assertEquals(2209, result.getDailyCalorieGoal());
        assertEquals(138.0, result.getDailyProteinGoal());
    }

    @Test
    void getCurrentUserGoal_whenGoalDoesNotExist_returnsNull() {
        UserEntity user = user("user@example.com", "MALE", 30, 180.0, 80.0, null);
        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(goalRepository.findByUser(user)).thenReturn(Optional.empty());

        var result = userGoalService.getCurrentUserGoal("user@example.com");

        org.junit.jupiter.api.Assertions.assertNull(result);
    }

    private UserEntity user(String email, String gender, Integer age, Double height, Double weight, Double bodyFat) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setGender(gender);
        user.setAge(age);
        user.setHeight(height);
        user.setWeight(weight);
        user.setBodyFatPercentage(bodyFat);
        return user;
    }

    private GoalCalculationRequestDto goal(GoalType goalType, ActivityLevel activityLevel, Double weeklyWeightChangeTargetKg) {
        GoalCalculationRequestDto goal = new GoalCalculationRequestDto();
        goal.setTargetWeight(75.0);
        goal.setGoalType(goalType);
        goal.setActivityLevel(activityLevel);
        goal.setWeeklyWeightChangeTargetKg(weeklyWeightChangeTargetKg);
        return goal;
    }
}
