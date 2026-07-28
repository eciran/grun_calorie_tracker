package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.ProgressLogEntity;
import com.grun.calorietracker.entity.SleepSessionEntity;
import com.grun.calorietracker.entity.BodyMeasurementEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.GoalType;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.repository.ProgressLogRepository;
import com.grun.calorietracker.repository.SleepSessionRepository;
import com.grun.calorietracker.repository.BodyMeasurementRepository;
import com.grun.calorietracker.service.impl.ProgressAnalyticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ProgressAnalyticsServiceImplTest {

    @Mock private SubscriptionService subscriptionService;
    @Mock private UserService userService;
    @Mock private UserGoalService userGoalService;
    @Mock private FoodLogsService foodLogsService;
    @Mock private ExerciseLogsService exerciseLogsService;
    @Mock private StepTrackingService stepTrackingService;
    @Mock private WaterTrackingService waterTrackingService;
    @Mock private FastingTrackingService fastingTrackingService;
    @Mock private ProgressLogRepository progressLogRepository;
    @Mock private BodyMeasurementRepository bodyMeasurementRepository;
    @Mock private SleepSessionRepository sleepSessionRepository;
    @Mock private UserAnalyticsCacheRevisionService analyticsCacheRevisionService;
    @Mock private com.grun.calorietracker.service.support.UserAnalyticsCacheGateway analyticsCacheGateway;
    @Mock private com.grun.calorietracker.service.support.UserAnalyticsCacheKeyFactory analyticsCacheKeyFactory;

    private ProgressAnalyticsServiceImpl service;
    private UserEntity user;
    private UserGoalDto goal;

    @BeforeEach
    void setUp() {
        service = new ProgressAnalyticsServiceImpl(
                subscriptionService,
                userService,
                userGoalService,
                foodLogsService,
                exerciseLogsService,
                stepTrackingService,
                waterTrackingService,
                fastingTrackingService,
                progressLogRepository,
                bodyMeasurementRepository,
                sleepSessionRepository,
                analyticsCacheRevisionService,
                analyticsCacheGateway,
                analyticsCacheKeyFactory
        );
        org.mockito.Mockito.lenient().when(analyticsCacheRevisionService.requireIdentity(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new com.grun.calorietracker.service.support.UserAnalyticsCacheIdentity(1L, 0L, "Europe/Dublin"));
        org.mockito.Mockito.lenient().when(analyticsCacheKeyFactory.key(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn("test-key");
        org.mockito.Mockito.lenient().doAnswer(invocation ->
                ((java.util.function.Supplier<?>) invocation.getArgument(2)).get())
                .when(analyticsCacheGateway).get(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.any());
        user = new UserEntity();
        user.setId(10L);
        user.setEmail("analytics@grun.app");
        user.setWeight(90.0);
        user.setTimeZone("Europe/Dublin");
        lenient().when(subscriptionService.hasFeatureAccess(anyString(), eq(SubscriptionFeature.WATER_TRACKING)))
                .thenReturn(true);

        goal = new UserGoalDto();
        goal.setTargetWeight(80.0);
        goal.setDailyCalorieGoal(2000);
        goal.setDailyProteinGoal(150.0);
        goal.setDailyCarbGoal(220.0);
        goal.setDailyFatGoal(65.0);
        goal.setGoalType(GoalType.LOSE_WEIGHT);
        goal.setCreatedAt(LocalDate.of(2026, 6, 1).atStartOfDay());
    }

    @Test
    void getAnalytics_UsesGoalBaselineAndDistinguishesMissingFoodDays() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 7);
        when(userService.findByEmail("analytics@grun.app")).thenReturn(Optional.of(user));
        when(userGoalService.getCurrentUserGoal("analytics@grun.app")).thenReturn(goal);

        FoodLogDailyStatsDto firstFood = food("2026-07-01", 1800, 140, 200, 60);
        FoodLogDailyStatsDto secondFood = food("2026-07-02", 2100, 150, 220, 65);
        FoodLogDailyStatsDto previousFood = food("2026-06-30", 2000, 145, 210, 62);
        when(foodLogsService.getDailyStats(eq("analytics@grun.app"), any(), any()))
                .thenReturn(List.of(firstFood, secondFood), List.of(previousFood));

        ExerciseLogsDto exercise = new ExerciseLogsDto();
        exercise.setLogDate(LocalDate.of(2026, 7, 2).atTime(18, 0));
        exercise.setCaloriesBurned(300.0);
        exercise.setDurationMinutes(45);
        when(exerciseLogsService.getExerciseLogsHistory(eq("analytics@grun.app"), any(), any()))
                .thenReturn(List.of(exercise), List.of());
        when(stepTrackingService.getRangeSummary(eq("analytics@grun.app"), any(), any()))
                .thenReturn(emptySteps(start, end), emptySteps(start.minusDays(7), end.minusDays(7)));
        when(waterTrackingService.getRangeSummary(eq("analytics@grun.app"), any(), any()))
                .thenReturn(emptyWater(start, end), emptyWater(start.minusDays(7), end.minusDays(7)));
        when(fastingTrackingService.getRangeSummary(eq("analytics@grun.app"), any(), any()))
                .thenReturn(emptyFasting(start, end), emptyFasting(start.minusDays(7), end.minusDays(7)));

        List<ProgressLogEntity> rangeWeights = List.of(
                weight(user, LocalDate.of(2026, 7, 1).atStartOfDay(), 91.0),
                weight(user, LocalDate.of(2026, 7, 7).atStartOfDay(), 90.0)
        );
        List<ProgressLogEntity> trendWeights = List.of(
                weight(user, LocalDate.of(2026, 6, 25).atStartOfDay(), 92.0),
                weight(user, LocalDate.of(2026, 7, 1).atStartOfDay(), 91.0),
                weight(user, LocalDate.of(2026, 7, 7).atStartOfDay(), 90.0)
        );
        when(progressLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), eq(start.atStartOfDay()), eq(end.plusDays(1).atStartOfDay())))
                .thenReturn(rangeWeights);
        when(progressLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), eq(end.minusDays(13).atStartOfDay()), eq(end.plusDays(1).atStartOfDay())))
                .thenReturn(trendWeights);
        when(progressLogRepository.findTopByUserAndLogDateLessThanOrderByLogDateDesc(user, end.plusDays(1).atStartOfDay()))
                .thenReturn(Optional.of(rangeWeights.get(1)));
        when(progressLogRepository.findTopByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                user, goal.getCreatedAt(), end.plusDays(1).atStartOfDay()))
                .thenReturn(Optional.of(weight(user, goal.getCreatedAt(), 100.0)));

        ProgressAnalyticsDto result = service.getAnalytics("analytics@grun.app", start, end, true);

        assertEquals(50.0, result.getBody().getGoalProgressPercent());
        assertEquals(-1.0, result.getOverview().getRangeWeightChangeKg());
        assertEquals("ON_TRACK", result.getOverview().getGoalTrendStatus());
        assertEquals(2, result.getDataCoverage().getFoodLoggedDays());
        assertEquals(7, result.getNutrition().getDailyPoints().size());
        assertNull(result.getNutrition().getDailyPoints().get(2).getConsumedCalories());
        assertFalse(result.getNutrition().getDailyPoints().get(2).isFoodLogged());
        assertEquals(100.0, result.getNutrition().getCalorieTargetAdherencePercent());
        assertEquals(0, result.getHabits().getCurrentDiaryStreakDays());
        assertEquals(2, result.getHabits().getBestDiaryStreakDays());
        assertNotNull(result.getPreviousPeriod());
        assertEquals(6, result.getComparisons().size());
        assertTrue(result.getComparisons().stream().anyMatch(metric ->
                "EXERCISE_MINUTES".equals(metric.getCode()) && Boolean.TRUE.equals(metric.getFavorable())));
        assertTrue(result.getInsights().stream().anyMatch(insight -> "IMPROVED".equals(insight.getCode())));
    }

    @Test
    void getAnalytics_BuildsNeutralBodyCompositionTrendsAndConfidence() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 7, 7);
        when(userService.findByEmail("analytics@grun.app")).thenReturn(Optional.of(user));
        when(userGoalService.getCurrentUserGoal("analytics@grun.app")).thenReturn(goal);
        when(foodLogsService.getDailyStats(eq("analytics@grun.app"), any(), any())).thenReturn(List.of());
        when(exerciseLogsService.getExerciseLogsHistory(eq("analytics@grun.app"), any(), any())).thenReturn(List.of());
        when(stepTrackingService.getRangeSummary(eq("analytics@grun.app"), any(), any())).thenReturn(emptySteps(start, end));
        when(waterTrackingService.getRangeSummary(eq("analytics@grun.app"), any(), any())).thenReturn(emptyWater(start, end));
        when(fastingTrackingService.getRangeSummary(eq("analytics@grun.app"), any(), any())).thenReturn(emptyFasting(start, end));
        when(progressLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of());
        when(progressLogRepository.findTopByUserAndLogDateLessThanOrderByLogDateDesc(eq(user), any()))
                .thenReturn(Optional.empty());
        when(progressLogRepository.findTopByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(Optional.empty());
        when(progressLogRepository.findByUserOrderByLogDateAsc(user)).thenReturn(List.of());

        List<BodyMeasurementEntity> measurements = List.of(
                measurement(user, LocalDate.of(2026, 6, 1).atTime(8, 0), 28.0, 96.0),
                measurement(user, LocalDate.of(2026, 6, 8).atTime(8, 0), 27.5, 95.0),
                measurement(user, LocalDate.of(2026, 6, 15).atTime(8, 0), 27.0, 94.0),
                measurement(user, LocalDate.of(2026, 6, 22).atTime(8, 0), 26.8, 93.5),
                measurement(user, LocalDate.of(2026, 6, 29).atTime(8, 0), 26.5, 93.0),
                measurement(user, LocalDate.of(2026, 7, 7).atTime(8, 0), 26.0, 92.0)
        );
        when(bodyMeasurementRepository
                .findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                        user, start.atStartOfDay(), end.plusDays(1).atStartOfDay()))
                .thenReturn(measurements);

        ProgressAnalyticsDto result = service.getAnalytics("analytics@grun.app", start, end, false);

        assertEquals("HIGH", result.getBodyComposition().getConfidence());
        assertEquals(6, result.getBodyComposition().getMeasurementCount());
        assertEquals(6, result.getDataCoverage().getBodyMeasurementRecordCount());
        ProgressAnalyticsDto.BodyMetricTrend bodyFat = result.getBodyComposition().getTrends().stream()
                .filter(metric -> "BODY_FAT".equals(metric.getCode()))
                .findFirst().orElseThrow();
        assertEquals("DOWN", bodyFat.getDirection());
        assertEquals(-2.0, bodyFat.getAbsoluteChange());
        assertEquals(6, bodyFat.getSampleCount());
        assertEquals("HIGH", bodyFat.getConfidence());
        ProgressAnalyticsDto.BodyMetricTrend chest = result.getBodyComposition().getTrends().stream()
                .filter(metric -> "CHEST".equals(metric.getCode()))
                .findFirst().orElseThrow();
        assertEquals("INSUFFICIENT_DATA", chest.getDirection());
        assertEquals(0, chest.getSampleCount());
        assertEquals("INSUFFICIENT_DATA", chest.getConfidence());
    }
    @Test
    void getAnalytics_DetectsWeightPlateauWhenGoalIsStillOpen() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 7, 1);
        stubEmptyPeriod(start, end);
        List<ProgressLogEntity> weights = List.of(
                weight(user, LocalDate.of(2026, 6, 4).atStartOfDay(), 90.0),
                weight(user, LocalDate.of(2026, 6, 11).atStartOfDay(), 90.02),
                weight(user, LocalDate.of(2026, 6, 18).atStartOfDay(), 89.99),
                weight(user, LocalDate.of(2026, 6, 25).atStartOfDay(), 90.01),
                weight(user, LocalDate.of(2026, 7, 1).atStartOfDay(), 90.0)
        );
        when(progressLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                user, start.atStartOfDay(), end.plusDays(1).atStartOfDay())).thenReturn(weights);
        when(progressLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                user, end.minusDays(13).atStartOfDay(), end.plusDays(1).atStartOfDay()))
                .thenReturn(weights.subList(2, weights.size()));
        when(progressLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                user, end.minusDays(27).atStartOfDay(), end.plusDays(1).atStartOfDay())).thenReturn(weights);
        when(progressLogRepository.findTopByUserAndLogDateLessThanOrderByLogDateDesc(
                user, end.plusDays(1).atStartOfDay())).thenReturn(Optional.of(weights.get(weights.size() - 1)));
        when(progressLogRepository.findTopByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                user, goal.getCreatedAt(), end.plusDays(1).atStartOfDay())).thenReturn(Optional.of(weights.get(0)));

        ProgressAnalyticsDto result = service.getAnalytics("analytics@grun.app", start, end, false);

        assertEquals("DETECTED", result.getWeightPlateau().getStatus());
        assertEquals(5, result.getWeightPlateau().getSampleCount());
        assertEquals(0.0, result.getWeightPlateau().getWeeklySlopeKg(), 0.02);
        assertTrue(result.getRelationships().stream().noneMatch(ProgressAnalyticsDto.Relationship::isSufficientData));
    }

    @Test
    void getAnalytics_ReturnsRelationshipOnlyWithEnoughVariableIntervals() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 7, 6);
        when(userService.findByEmail("analytics@grun.app")).thenReturn(Optional.of(user));
        when(userGoalService.getCurrentUserGoal("analytics@grun.app")).thenReturn(goal);

        List<FoodLogDailyStatsDto> foodDays = new ArrayList<>();
        for (LocalDate date = start.plusDays(1); !date.isAfter(end); date = date.plusDays(1)) {
            int interval = (int) (java.time.temporal.ChronoUnit.DAYS.between(start.plusDays(1), date) / 7);
            foodDays.add(food(date.toString(), 1800 + interval * 100, 140, 200, 60));
        }
        when(foodLogsService.getDailyStats(
                "analytics@grun.app", start.atStartOfDay(), end.plusDays(1).atStartOfDay())).thenReturn(foodDays);
        when(exerciseLogsService.getExerciseLogsHistory(
                "analytics@grun.app", start.atStartOfDay(), end.plusDays(1).atStartOfDay())).thenReturn(List.of());
        when(stepTrackingService.getRangeSummary("analytics@grun.app", start, end)).thenReturn(emptySteps(start, end));
        when(waterTrackingService.getRangeSummary("analytics@grun.app", start, end)).thenReturn(emptyWater(start, end));
        when(fastingTrackingService.getRangeSummary("analytics@grun.app", start, end)).thenReturn(emptyFasting(start, end));

        List<ProgressLogEntity> weights = List.of(
                weight(user, LocalDate.of(2026, 6, 1).atStartOfDay(), 90.0),
                weight(user, LocalDate.of(2026, 6, 8).atStartOfDay(), 89.5),
                weight(user, LocalDate.of(2026, 6, 15).atStartOfDay(), 89.25),
                weight(user, LocalDate.of(2026, 6, 22).atStartOfDay(), 89.25),
                weight(user, LocalDate.of(2026, 6, 29).atStartOfDay(), 89.5),
                weight(user, LocalDate.of(2026, 7, 6).atStartOfDay(), 90.0)
        );
        when(progressLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                user, start.atStartOfDay(), end.plusDays(1).atStartOfDay())).thenReturn(weights);
        when(progressLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                user, end.minusDays(13).atStartOfDay(), end.plusDays(1).atStartOfDay()))
                .thenReturn(weights.subList(4, weights.size()));
        when(progressLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                user, end.minusDays(27).atStartOfDay(), end.plusDays(1).atStartOfDay()))
                .thenReturn(weights.subList(2, weights.size()));
        when(progressLogRepository.findTopByUserAndLogDateLessThanOrderByLogDateDesc(
                user, end.plusDays(1).atStartOfDay())).thenReturn(Optional.of(weights.get(weights.size() - 1)));
        when(progressLogRepository.findTopByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                user, goal.getCreatedAt(), end.plusDays(1).atStartOfDay())).thenReturn(Optional.of(weights.get(0)));

        List<SleepSessionEntity> sleepSessions = new ArrayList<>();
        for (LocalDate date = start.plusDays(1); !date.isAfter(end); date = date.plusDays(1)) {
            int interval = (int) (java.time.temporal.ChronoUnit.DAYS.between(start.plusDays(1), date) / 7);
            SleepSessionEntity sleep = new SleepSessionEntity();
            sleep.setSleepDate(date);
            sleep.setDurationMinutes(420 + interval * 30);
            sleepSessions.add(sleep);
        }
        when(sleepSessionRepository.findByUserAndSleepDateBetweenOrderByStartedAtAsc(user, start, end))
                .thenReturn(sleepSessions);

        ProgressAnalyticsDto result = service.getAnalytics("analytics@grun.app", start, end, false);
        ProgressAnalyticsDto.Relationship calories = result.getRelationships().stream()
                .filter(relationship -> "CALORIE_INTAKE_TO_WEIGHT_CHANGE".equals(relationship.getCode()))
                .findFirst().orElseThrow();

        assertTrue(calories.isSufficientData());
        assertEquals(5, calories.getSampleSize());
        assertEquals("POSITIVE", calories.getDirection());
        assertEquals("STRONG", calories.getStrength());
        assertTrue(calories.getCoefficient() > 0.95);
        ProgressAnalyticsDto.Relationship sleep = result.getRelationships().stream()
                .filter(relationship -> "SLEEP_DURATION_TO_WEIGHT_CHANGE".equals(relationship.getCode()))
                .findFirst().orElseThrow();
        assertTrue(sleep.isSufficientData());
        assertEquals(5, sleep.getSampleSize());
        assertEquals("POSITIVE", sleep.getDirection());
        assertEquals("STRONG", sleep.getStrength());
        assertEquals(35, result.getDataCoverage().getSleepDataDays());
    }
    @Test
    void getAnalytics_ProjectsGoalWithExplicitConfidenceAndAdaptiveAggregation() {
        LocalDate start = LocalDate.of(2026, 5, 31);
        LocalDate end = LocalDate.of(2026, 7, 1);
        stubEmptyPeriod(start, end);
        List<ProgressLogEntity> weights = List.of(
                weight(user, LocalDate.of(2026, 6, 4).atStartOfDay(), 90.0),
                weight(user, LocalDate.of(2026, 6, 11).atStartOfDay(), 89.5),
                weight(user, LocalDate.of(2026, 6, 18).atStartOfDay(), 89.0),
                weight(user, LocalDate.of(2026, 6, 25).atStartOfDay(), 88.5),
                weight(user, LocalDate.of(2026, 7, 1).atStartOfDay(), 88.0)
        );
        when(progressLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                user, start.atStartOfDay(), end.plusDays(1).atStartOfDay())).thenReturn(weights);
        when(progressLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                user, end.minusDays(13).atStartOfDay(), end.plusDays(1).atStartOfDay()))
                .thenReturn(weights.subList(3, weights.size()));
        when(progressLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                user, end.minusDays(27).atStartOfDay(), end.plusDays(1).atStartOfDay())).thenReturn(weights);
        when(progressLogRepository.findTopByUserAndLogDateLessThanOrderByLogDateDesc(
                user, end.plusDays(1).atStartOfDay())).thenReturn(Optional.of(weights.get(weights.size() - 1)));
        when(progressLogRepository.findTopByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                user, goal.getCreatedAt(), end.plusDays(1).atStartOfDay())).thenReturn(Optional.of(weights.get(0)));

        ProgressAnalyticsDto result = service.getAnalytics("analytics@grun.app", start, end, false);

        assertEquals("WEEK", result.getRange().getAggregation());
        assertEquals("PROJECTED", result.getBody().getProjectionStatus());
        assertEquals("MEDIUM", result.getBody().getProjectionConfidence());
        assertEquals(5, result.getBody().getProjectionSampleCount());
        assertEquals(27, result.getBody().getProjectionObservedDaySpan());
        assertNotNull(result.getBody().getProjectedGoalDate());
        assertTrue(result.getBody().getProjectionWeeklySlopeKg() < 0);
    }
    @Test
    void getAnalytics_WhenRangeExceedsLimit_RejectsRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.getAnalytics(
                "analytics@grun.app",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 2),
                false));
    }

    private void stubEmptyPeriod(LocalDate start, LocalDate end) {
        when(userService.findByEmail("analytics@grun.app")).thenReturn(Optional.of(user));
        when(userGoalService.getCurrentUserGoal("analytics@grun.app")).thenReturn(goal);
        when(foodLogsService.getDailyStats(
                "analytics@grun.app", start.atStartOfDay(), end.plusDays(1).atStartOfDay())).thenReturn(List.of());
        when(exerciseLogsService.getExerciseLogsHistory(
                "analytics@grun.app", start.atStartOfDay(), end.plusDays(1).atStartOfDay())).thenReturn(List.of());
        when(stepTrackingService.getRangeSummary("analytics@grun.app", start, end)).thenReturn(emptySteps(start, end));
        when(waterTrackingService.getRangeSummary("analytics@grun.app", start, end)).thenReturn(emptyWater(start, end));
        when(fastingTrackingService.getRangeSummary("analytics@grun.app", start, end)).thenReturn(emptyFasting(start, end));
    }
    private FoodLogDailyStatsDto food(String date, double calories, double protein, double carbs, double fat) {
        FoodLogDailyStatsDto dto = new FoodLogDailyStatsDto();
        dto.setDate(date);
        dto.setTotalCalories(calories);
        dto.setTotalProtein(protein);
        dto.setTotalCarbs(carbs);
        dto.setTotalFat(fat);
        return dto;
    }

    private BodyMeasurementEntity measurement(
            UserEntity owner,
            LocalDateTime recordedAt,
            double bodyFatPercentage,
            double waistCm
    ) {
        BodyMeasurementEntity entity = new BodyMeasurementEntity();
        entity.setUser(owner);
        entity.setRecordedAt(recordedAt);
        entity.setBodyFatPercentage(bodyFatPercentage);
        entity.setWaistCm(waistCm);
        return entity;
    }
    private ProgressLogEntity weight(UserEntity owner, LocalDateTime date, double value) {
        ProgressLogEntity entity = new ProgressLogEntity();
        entity.setUser(owner);
        entity.setLogDate(date);
        entity.setWeight(value);
        return entity;
    }

    private StepRangeSummaryDto emptySteps(LocalDate start, LocalDate end) {
        StepRangeSummaryDto dto = new StepRangeSummaryDto();
        dto.setStartDate(start);
        dto.setEndDate(end);
        dto.setDays(List.of());
        dto.setTargetHitDays(0);
        return dto;
    }

    private WaterRangeSummaryDto emptyWater(LocalDate start, LocalDate end) {
        WaterRangeSummaryDto dto = new WaterRangeSummaryDto();
        dto.setStartDate(start);
        dto.setEndDate(end);
        dto.setDays(List.of());
        dto.setTargetHitDays(0);
        return dto;
    }

    private FastingRangeSummaryDto emptyFasting(LocalDate start, LocalDate end) {
        FastingRangeSummaryDto dto = new FastingRangeSummaryDto();
        dto.setStartDate(start);
        dto.setEndDate(end);
        dto.setDailyTrends(List.of());
        dto.setCompletedSessionCount(0);
        return dto;
    }
}
