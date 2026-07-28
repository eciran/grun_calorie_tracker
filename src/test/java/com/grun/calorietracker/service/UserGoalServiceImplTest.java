package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.dto.GoalCalculationRequestDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.GoalType;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.service.impl.UserGoalServiceImpl;
import com.grun.calorietracker.service.support.ProfileEnergyExpenditureCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserGoalServiceImplTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private UserService userService;
    @Mock
    private UserAnalyticsCacheRevisionService analyticsCacheRevisionService;

    private UserGoalServiceImpl userGoalService;

    @BeforeEach
    void setUp() {
        userGoalService = new UserGoalServiceImpl(goalRepository, userService, new ProfileEnergyExpenditureCalculator(), analyticsCacheRevisionService);
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
        assertEquals("MIFFLIN_ST_JEOR", result.getFormula());
        assertEquals(1780.0, result.getBmr());
        assertEquals(2136, result.getMaintenanceCalories());
        assertEquals(0.0, result.getEffectiveWeeklyRateKg());
        assertEquals(0, result.getEstimatedDurationWeeks());
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
        assertEquals(40, result.getEstimatedDurationWeeks());
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
    void calculateGoalPreview_usesDraftMetricsWithoutLoadingOrSavingUser() {
        UserProfileDto profile = UserProfileDto.builder()
                .gender("MALE")
                .age(30)
                .height(180.0)
                .weight(80.0)
                .build();
        GoalCalculationRequestDto goal = goal(GoalType.LOSE_WEIGHT, ActivityLevel.MODERATE, 0.5);

        GoalCalculationResponse result = userGoalService.calculateGoalPreview(goal, profile);

        assertEquals(2209, result.getCalculatedCalorieNeed());
        org.mockito.Mockito.verifyNoInteractions(userService, goalRepository);
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


    @Test
    void calculateGoal_rejectsGoalDirectionThatConflictsWithTargetWeight() {
        UserEntity user = user("user@example.com", "MALE", 30, 180.0, 80.0, null);
        GoalCalculationRequestDto goal = goal(GoalType.GAIN_WEIGHT, ActivityLevel.MODERATE, 0.3);
        goal.setTargetWeight(65.0);
        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> userGoalService.calculateGoal(goal, "user@example.com")
        );

        assertTrue(error.getMessage().contains("higher than the current weight"));
    }

    @Test
    void calculateGoal_capsUnsafeWeeklyRateAndExplainsAdjustment() {
        UserEntity user = user("user@example.com", "MALE", 30, 180.0, 80.0, null);
        GoalCalculationRequestDto goal = goal(GoalType.LOSE_WEIGHT, ActivityLevel.MODERATE, 2.0);
        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        GoalCalculationResponse result = userGoalService.calculateGoal(goal, "user@example.com");

        assertEquals(2.0, result.getRequestedWeeklyRateKg());
        assertEquals(-0.8, result.getEffectiveWeeklyRateKg());
        assertTrue(result.isSafetyAdjusted());
        assertNotNull(result.getSafetyWarning());
        assertEquals(-880, result.getCalorieAdjustment());
        assertEquals(25, result.getEstimatedDurationWeeks());
    }

    @Test
    void calculateGoal_appliesMinimumCalorieFloorAndReportsIt() {
        UserEntity user = user("user@example.com", "FEMALE", 35, 165.0, 45.0, null);
        GoalCalculationRequestDto goal = goal(GoalType.LOSE_WEIGHT, ActivityLevel.SEDENTARY, 1.0);
        goal.setTargetWeight(40.0);
        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        GoalCalculationResponse result = userGoalService.calculateGoal(goal, "user@example.com");

        assertEquals(1200, result.getCalculatedCalorieNeed());
        assertEquals(1200, result.getMinimumCalorieFloor());
        assertTrue(result.isSafetyAdjusted());
        assertTrue(result.getSafetyWarning().contains("minimum safety floor"));
    }

    @Test
    void saveUserGoal_invalidReplacementDoesNotDeleteExistingGoal() {
        UserEntity user = user("user@example.com", "MALE", 30, 180.0, 80.0, null);
        GoalCalculationRequestDto goal = goal(GoalType.GAIN_WEIGHT, ActivityLevel.MODERATE, 0.3);
        goal.setTargetWeight(65.0);
        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> userGoalService.saveUserGoal(goal, "user@example.com"));

        verify(goalRepository, never()).delete(any(UserGoalEntity.class));
        verify(goalRepository, never()).save(any(UserGoalEntity.class));
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
        goal.setTargetWeight(switch (goalType) {
            case LOSE_WEIGHT -> 60.0;
            case GAIN_WEIGHT -> 85.0;
            case BUILD_MUSCLE, MAINTAIN_WEIGHT -> 80.0;
        });
        goal.setGoalType(goalType);
        goal.setActivityLevel(activityLevel);
        goal.setWeeklyWeightChangeTargetKg(weeklyWeightChangeTargetKg);
        return goal;
    }
}
