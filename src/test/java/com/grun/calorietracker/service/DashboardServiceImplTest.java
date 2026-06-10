package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.DailySummaryDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.ExerciseLogRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.repository.ProgressLogRepository;
import com.grun.calorietracker.repository.RecipeLogRepository;
import com.grun.calorietracker.service.impl.DashboardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class DashboardServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private FoodLogsRepository foodLogsRepository;

    @Mock
    private ExerciseLogRepository exerciseLogRepository;

    @Mock
    private ProgressLogRepository progressLogRepository;

    @Mock
    private RecipeLogRepository recipeLogRepository;

    @Mock
    private HealthIntegrationService healthIntegrationService;

    @Mock
    private SubscriptionService subscriptionService;

    private DashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dashboardService = new DashboardServiceImpl(
                userService,
                goalRepository,
                foodLogsRepository,
                exerciseLogRepository,
                progressLogRepository,
                recipeLogRepository,
                healthIntegrationService,
                subscriptionService
        );
    }

    @Test
    void getDailySummary_whenNoLogsOrGoal_returnsZeroTotalsAndProfileWeight() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setWeight(82.0);
        LocalDate date = LocalDate.of(2026, 5, 15);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(foodLogsRepository.getSummaryTotalsByUserAndDateBetween(eq(1L), eq(start), eq(end))).thenReturn(List.of());
        when(recipeLogRepository.getSummaryTotalsByUserAndDateBetween(eq(1L), eq(start), eq(end))).thenReturn(List.of());
        when(foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(eq(user), eq(start), eq(end)))
                .thenReturn(List.of());
        when(exerciseLogRepository.getSummaryTotalsByUserAndDateBetween(eq(1L), eq(start), eq(end))).thenReturn(List.of());
        when(exerciseLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(eq(user), eq(start), eq(end)))
                .thenReturn(List.of());
        when(goalRepository.findByUser(user)).thenReturn(Optional.empty());
        when(progressLogRepository.findTopByUserOrderByLogDateDesc(user)).thenReturn(Optional.empty());
        com.grun.calorietracker.dto.HealthDailySummaryDto healthSummary = new com.grun.calorietracker.dto.HealthDailySummaryDto();
        healthSummary.setHasHealthData(false);
        when(healthIntegrationService.getDailySummary("user@example.com", date)).thenReturn(healthSummary);
        when(subscriptionService.hasFeatureAccess("user@example.com", com.grun.calorietracker.enums.SubscriptionFeature.HEALTH_INTEGRATION))
                .thenReturn(true);

        DailySummaryDto result = dashboardService.getDailySummary("user@example.com", date);

        assertEquals(date, result.getSummaryDate());
        assertEquals(0, result.getTargetCalories());
        assertEquals(0.0, result.getConsumedCalories());
        assertEquals(0.0, result.getBurnedCalories());
        assertEquals(0.0, result.getRemainingCalories());
        assertEquals(0.0, result.getNetCalories());
        assertEquals(0.0, result.getCalorieProgressPercent());
        assertEquals(0.0, result.getConsumedProtein());
        assertEquals(0.0, result.getConsumedFat());
        assertEquals(0.0, result.getConsumedCarbs());
        assertEquals(0.0, result.getRemainingProtein());
        assertEquals(0.0, result.getRemainingFat());
        assertEquals(0.0, result.getRemainingCarbs());
        assertEquals(0.0, result.getProteinProgressPercent());
        assertEquals(0.0, result.getFatProgressPercent());
        assertEquals(0.0, result.getCarbsProgressPercent());
        assertEquals(0, result.getTotalExerciseMinutes());
        assertEquals(82.0, result.getCurrentWeight());
        assertEquals(false, result.getHasActiveGoal());
        assertEquals(false, result.getOnboardingCompleted());
        assertEquals(false, result.getHasFoodLogs());
        assertEquals(false, result.getHasExerciseLogs());
        assertEquals(false, result.getHasAnyDiaryEntry());
        assertEquals(List.of(), result.getFoodLogs());
        assertEquals(List.of(), result.getExerciseLogs());
        assertEquals(false, result.getHealthSummary().getHasHealthData());
    }

    @Test
    void getDailySummary_whenOnboardingComplete_returnsRemainingMacrosAndProgress() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setAge(32);
        user.setGender("MALE");
        user.setHeight(180.0);
        user.setWeight(82.0);
        com.grun.calorietracker.entity.UserGoalEntity goal = new com.grun.calorietracker.entity.UserGoalEntity();
        goal.setDailyCalorieGoal(2200);
        goal.setDailyProteinGoal(140.0);
        goal.setDailyFatGoal(70.0);
        goal.setDailyCarbGoal(250.0);
        goal.setTargetWeight(78.0);
        goal.setGoalType(com.grun.calorietracker.enums.GoalType.LOSE_WEIGHT);
        LocalDate date = LocalDate.of(2026, 5, 20);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(foodLogsRepository.getSummaryTotalsByUserAndDateBetween(eq(1L), eq(start), eq(end)))
                .thenReturn(Collections.singletonList(new Object[]{1100.0, 70.0, 125.0, 35.0}));
        when(recipeLogRepository.getSummaryTotalsByUserAndDateBetween(eq(1L), eq(start), eq(end)))
                .thenReturn(Collections.singletonList(new Object[]{0.0, 0.0, 0.0, 0.0}));
        when(foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(eq(user), eq(start), eq(end)))
                .thenReturn(List.of());
        when(exerciseLogRepository.getSummaryTotalsByUserAndDateBetween(eq(1L), eq(start), eq(end)))
                .thenReturn(Collections.singletonList(new Object[]{300.0, 45}));
        when(exerciseLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(eq(user), eq(start), eq(end)))
                .thenReturn(List.of());
        when(goalRepository.findByUser(user)).thenReturn(Optional.of(goal));
        when(progressLogRepository.findTopByUserOrderByLogDateDesc(user)).thenReturn(Optional.empty());
        when(healthIntegrationService.getDailySummary("user@example.com", date))
                .thenReturn(new com.grun.calorietracker.dto.HealthDailySummaryDto());
        when(subscriptionService.hasFeatureAccess("user@example.com", com.grun.calorietracker.enums.SubscriptionFeature.HEALTH_INTEGRATION))
                .thenReturn(true);

        DailySummaryDto result = dashboardService.getDailySummary("user@example.com", date);

        assertEquals(1400.0, result.getRemainingCalories());
        assertEquals(800.0, result.getNetCalories());
        assertEquals(50.0, result.getCalorieProgressPercent());
        assertEquals(70.0, result.getRemainingProtein());
        assertEquals(35.0, result.getRemainingFat());
        assertEquals(125.0, result.getRemainingCarbs());
        assertEquals(50.0, result.getProteinProgressPercent());
        assertEquals(50.0, result.getFatProgressPercent());
        assertEquals(50.0, result.getCarbsProgressPercent());
        assertEquals(true, result.getHasActiveGoal());
        assertEquals(true, result.getOnboardingCompleted());
        assertEquals(false, result.getHasFoodLogs());
        assertEquals(false, result.getHasExerciseLogs());
        assertEquals(false, result.getHasAnyDiaryEntry());
    }
}
