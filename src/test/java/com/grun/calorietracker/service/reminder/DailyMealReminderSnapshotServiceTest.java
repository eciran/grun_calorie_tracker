package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.enums.FastingSessionStatus;
import com.grun.calorietracker.enums.MealPlanItemConsumptionStatus;
import com.grun.calorietracker.repository.FastingSessionRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.repository.MealPlanItemConsumptionRepository;
import com.grun.calorietracker.repository.RecipeLogRepository;
import com.grun.calorietracker.repository.projection.MealReminderMealAggregateProjection;
import com.grun.calorietracker.repository.projection.MealReminderUnresolvedPlanProjection;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyMealReminderSnapshotServiceTest {

    @Mock private FoodLogsRepository foodLogsRepository;
    @Mock private RecipeLogRepository recipeLogRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private FastingSessionRepository fastingSessionRepository;
    @Mock private MealPlanItemConsumptionRepository mealPlanItemConsumptionRepository;

    private DailyMealReminderSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new DailyMealReminderSnapshotService(
                foodLogsRepository,
                recipeLogRepository,
                goalRepository,
                fastingSessionRepository,
                mealPlanItemConsumptionRepository,
                new UserTimeZoneSupport());
    }

    @Test
    void buildBatch_usesCanonicalFoodAndRecipeTotalsAndEffectiveGoal() {
        UserEntity user = user(7L, "Europe/Dublin");
        Instant evaluatedAt = Instant.parse("2026-08-29T09:15:00Z");
        LocalDate date = LocalDate.of(2026, 8, 29);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        when(foodLogsRepository.aggregateMealReminderByUsersAndDate(List.of(7L), start, end))
                .thenReturn(List.of(row(7L, "BREAKFAST", 1L, 420.25), row(7L, "DINNER", 1L, 350.0)));
        when(recipeLogRepository.aggregateMealReminderByUsersAndDate(List.of(7L), start, end))
                .thenReturn(List.of(row(7L, "BREAKFAST", 1L, 180.25)));
        UserGoalEntity goal = goal(user, 99L, 3L, 2_000, date);
        when(goalRepository.findAllByUserIdIn(List.of(7L))).thenReturn(List.of(goal));
        when(fastingSessionRepository.findDistinctUserIdsByStatus(List.of(7L), FastingSessionStatus.ACTIVE))
                .thenReturn(List.of());

        DailyMealReminderSnapshot snapshot = service.buildBatch(List.of(user), evaluatedAt).get(0);

        assertEquals(date, snapshot.localDate());
        assertEquals("Europe/Dublin", snapshot.timeZone());
        assertEquals(99L, snapshot.goalId());
        assertEquals(3L, snapshot.goalVersion());
        assertEquals("EFFECTIVE_DATE", snapshot.goalResolutionSource());
        assertEquals(2000, snapshot.targetCalories());
        assertEquals(770.25, snapshot.foodCalories());
        assertEquals(180.25, snapshot.recipeCalories());
        assertEquals(950.5, snapshot.consumedCalories());
        assertEquals(1049.5, snapshot.remainingCalories());
        assertTrue(snapshot.calorieDataReliable());
        assertTrue(snapshot.targetValid());
        assertEquals(2L, snapshot.mealTotals().get(MealReminderContract.Meal.BREAKFAST).recordCount());
        assertEquals(MealReminderContract.MealState.RECORDED,
                snapshot.mealStates().get(MealReminderContract.Meal.BREAKFAST));
        assertEquals(MealReminderContract.MealState.MISSING,
                snapshot.mealStates().get(MealReminderContract.Meal.LUNCH));
        assertEquals(DailyMealReminderSnapshot.ActivityState.INACTIVE, snapshot.fastingState());
    }

    @Test
    void buildBatch_groupsBoundedQueriesByUsersLocalDateAcrossDst() {
        UserEntity dublin = user(1L, "Europe/Dublin");
        UserEntity newYork = user(2L, "America/New_York");
        Instant evaluatedAt = Instant.parse("2026-03-29T00:30:00Z");
        LocalDate dublinDate = LocalDate.of(2026, 3, 29);
        LocalDate newYorkDate = LocalDate.of(2026, 3, 28);

        stubEmptyDateBatch(List.of(1L), dublinDate);
        stubEmptyDateBatch(List.of(2L), newYorkDate);

        List<DailyMealReminderSnapshot> snapshots = service.buildBatch(List.of(dublin, newYork), evaluatedAt);

        assertEquals(dublinDate, snapshots.get(0).localDate());
        assertEquals(newYorkDate, snapshots.get(1).localDate());
        verify(foodLogsRepository).aggregateMealReminderByUsersAndDate(
                List.of(1L), dublinDate.atStartOfDay(), dublinDate.plusDays(1).atStartOfDay());
        verify(foodLogsRepository).aggregateMealReminderByUsersAndDate(
                List.of(2L), newYorkDate.atStartOfDay(), newYorkDate.plusDays(1).atStartOfDay());
    }

    @Test
    void buildBatch_rebuildsWithoutCacheAfterDiaryOrGoalChanges() {
        UserEntity user = user(5L, "Europe/Dublin");
        Instant evaluatedAt = Instant.parse("2026-08-29T12:00:00Z");
        LocalDate date = LocalDate.of(2026, 8, 29);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        when(foodLogsRepository.aggregateMealReminderByUsersAndDate(List.of(5L), start, end))
                .thenReturn(List.of(), List.of(row(5L, "LUNCH", 1L, 625.0)));
        when(recipeLogRepository.aggregateMealReminderByUsersAndDate(List.of(5L), start, end))
                .thenReturn(List.of());
        UserGoalEntity firstGoal = goal(user, 10L, 1L, 2_000, date);
        UserGoalEntity changedGoal = goal(user, 11L, 2L, 2_200, date);
        when(goalRepository.findAllByUserIdIn(List.of(5L)))
                .thenReturn(List.of(firstGoal), List.of(changedGoal));
        when(fastingSessionRepository.findDistinctUserIdsByStatus(List.of(5L), FastingSessionStatus.ACTIVE))
                .thenReturn(List.of());

        DailyMealReminderSnapshot before = service.buildBatch(List.of(user), evaluatedAt).get(0);
        DailyMealReminderSnapshot after = service.buildBatch(List.of(user), evaluatedAt.plusSeconds(30)).get(0);

        assertEquals(0.0, before.consumedCalories());
        assertEquals(2000, before.targetCalories());
        assertEquals(625.0, after.consumedCalories());
        assertEquals(2200, after.targetCalories());
        assertEquals(MealReminderContract.MealState.RECORDED,
                after.mealStates().get(MealReminderContract.Meal.LUNCH));
        verify(foodLogsRepository, times(2)).aggregateMealReminderByUsersAndDate(List.of(5L), start, end);
    }

    @Test
    void buildBatch_dataFailureBecomesUnknownAndNeverZeroCalories() {
        UserEntity user = user(8L, "Europe/Dublin");
        Instant evaluatedAt = Instant.parse("2026-08-29T12:00:00Z");
        LocalDate date = LocalDate.of(2026, 8, 29);
        when(foodLogsRepository.aggregateMealReminderByUsersAndDate(
                List.of(8L), date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                .thenThrow(new IllegalStateException("database unavailable"));
        when(recipeLogRepository.aggregateMealReminderByUsersAndDate(
                List.of(8L), date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                .thenReturn(List.of());
        when(goalRepository.findAllByUserIdIn(List.of(8L))).thenReturn(List.of());
        when(fastingSessionRepository.findDistinctUserIdsByStatus(List.of(8L), FastingSessionStatus.ACTIVE))
                .thenReturn(List.of());

        DailyMealReminderSnapshot snapshot = service.buildBatch(List.of(user), evaluatedAt).get(0);

        assertFalse(snapshot.calorieDataReliable());
        assertNull(snapshot.foodCalories());
        assertNull(snapshot.consumedCalories());
        assertNull(snapshot.remainingCalories());
        assertEquals(MealReminderContract.MealState.UNKNOWN,
                snapshot.mealStates().get(MealReminderContract.Meal.BREAKFAST));
    }

    @Test
    void buildBatch_unresolvedPartialOrReplacementIsUnknownAndNeverAddsPlanSnapshotCalories() {
        UserEntity user = user(12L, "Europe/Dublin");
        Instant evaluatedAt = Instant.parse("2026-08-29T12:00:00Z");
        LocalDate date = LocalDate.of(2026, 8, 29);
        when(foodLogsRepository.aggregateMealReminderByUsersAndDate(
                List.of(12L), date.atStartOfDay(), date.plusDays(1).atStartOfDay())).thenReturn(List.of());
        when(recipeLogRepository.aggregateMealReminderByUsersAndDate(
                List.of(12L), date.atStartOfDay(), date.plusDays(1).atStartOfDay())).thenReturn(List.of());
        when(mealPlanItemConsumptionRepository.findUnresolvedReminderConsumptions(
                List.of(12L),
                date,
                List.of(MealPlanItemConsumptionStatus.LOGGED,
                        MealPlanItemConsumptionStatus.PARTIALLY_CONSUMED,
                        MealPlanItemConsumptionStatus.REPLACED)))
                .thenReturn(List.of(unresolvedRow(12L, "DINNER")));
        UserGoalEntity goal = goal(user, 20L, 1L, 2_000, date);
        when(goalRepository.findAllByUserIdIn(List.of(12L))).thenReturn(List.of(goal));
        when(fastingSessionRepository.findDistinctUserIdsByStatus(List.of(12L), FastingSessionStatus.ACTIVE))
                .thenReturn(List.of());

        DailyMealReminderSnapshot snapshot = service.buildBatch(List.of(user), evaluatedAt).get(0);

        assertEquals(MealReminderContract.MealState.UNKNOWN,
                snapshot.mealStates().get(MealReminderContract.Meal.DINNER));
        assertEquals(MealReminderContract.MealState.MISSING,
                snapshot.mealStates().get(MealReminderContract.Meal.BREAKFAST));
        assertFalse(snapshot.calorieDataReliable());
        assertNull(snapshot.consumedCalories());
    }

    @Test
    void buildBatch_invalidIanaZoneProducesUnavailableSnapshotWithoutQueryingRepositories() {
        UserEntity user = user(9L, "Europe/Not-A-Zone");

        DailyMealReminderSnapshot snapshot = service.buildBatch(
                List.of(user), Instant.parse("2026-08-29T12:00:00Z")).get(0);

        assertNull(snapshot.localDate());
        assertFalse(snapshot.calorieDataReliable());
        assertEquals(DailyMealReminderSnapshot.ActivityState.UNKNOWN, snapshot.fastingState());
        assertEquals(MealReminderContract.MealState.UNKNOWN,
                snapshot.mealStates().get(MealReminderContract.Meal.DINNER));
    }

    @Test
    void buildBatch_rejectsUnboundedCandidateInput() {
        List<UserEntity> users = java.util.stream.LongStream.rangeClosed(1, 501)
                .mapToObj(id -> user(id, "Europe/Dublin"))
                .toList();

        assertThrows(IllegalArgumentException.class,
                () -> service.buildBatch(users, Instant.parse("2026-08-29T12:00:00Z")));
    }

    private void stubEmptyDateBatch(List<Long> ids, LocalDate date) {
        when(foodLogsRepository.aggregateMealReminderByUsersAndDate(
                ids, date.atStartOfDay(), date.plusDays(1).atStartOfDay())).thenReturn(List.of());
        when(recipeLogRepository.aggregateMealReminderByUsersAndDate(
                ids, date.atStartOfDay(), date.plusDays(1).atStartOfDay())).thenReturn(List.of());
        when(goalRepository.findAllByUserIdIn(ids)).thenReturn(List.of());
        when(fastingSessionRepository.findDistinctUserIdsByStatus(ids, FastingSessionStatus.ACTIVE))
                .thenReturn(List.of());
    }

    private UserEntity user(Long id, String timeZone) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setTimeZone(timeZone);
        user.setPushNotificationsEnabled(true);
        user.setMealRemindersEnabled(true);
        return user;
    }

    private UserGoalEntity goal(
            UserEntity user,
            Long id,
            Long version,
            int calories,
            LocalDate effectiveLocalDate
    ) {
        UserGoalEntity goal = new UserGoalEntity();
        goal.setId(id);
        goal.setVersion(version);
        goal.setUser(user);
        goal.setDailyCalorieGoal(calories);
        goal.setEffectiveLocalDate(effectiveLocalDate);
        goal.setEffectiveFrom(effectiveLocalDate.atStartOfDay());
        return goal;
    }

    private MealReminderMealAggregateProjection row(Long userId, String meal, Long count, Double calories) {
        return new MealReminderMealAggregateProjection() {
            @Override public Long getUserId() { return userId; }
            @Override public String getMealType() { return meal; }
            @Override public Long getRecordCount() { return count; }
            @Override public Double getCalories() { return calories; }
        };
    }

    private MealReminderUnresolvedPlanProjection unresolvedRow(Long userId, String meal) {
        return new MealReminderUnresolvedPlanProjection() {
            @Override public Long getUserId() { return userId; }
            @Override public String getMealType() { return meal; }
        };
    }
}
