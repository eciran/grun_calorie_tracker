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
import com.grun.calorietracker.service.support.DailyCalorieBudgetSupport;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DailyMealReminderSnapshotService {

    public static final int MAX_BATCH_SIZE = 500;

    private final FoodLogsRepository foodLogsRepository;
    private final RecipeLogRepository recipeLogRepository;
    private final GoalRepository goalRepository;
    private final FastingSessionRepository fastingSessionRepository;
    private final MealPlanItemConsumptionRepository mealPlanItemConsumptionRepository;
    private final UserTimeZoneSupport timeZoneSupport;

    /**
     * Builds fresh, non-cached snapshots for an already bounded candidate page. The scheduler owns paging;
     * this service deliberately has no "find all users" dependency.
     */
    @Transactional(readOnly = true)
    public List<DailyMealReminderSnapshot> buildBatch(List<UserEntity> candidates, Instant evaluatedAt) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Meal reminder snapshot batch cannot exceed " + MAX_BATCH_SIZE);
        }
        if (evaluatedAt == null) {
            throw new IllegalArgumentException("evaluatedAt is required");
        }

        Map<LocalDate, List<CandidateContext>> byLocalDate = new LinkedHashMap<>();
        List<CandidateContext> invalidTimeZones = new ArrayList<>();
        Set<Long> uniqueUserIds = new HashSet<>();
        for (UserEntity user : candidates) {
            if (user == null || user.getId() == null || !uniqueUserIds.add(user.getId())) {
                throw new IllegalArgumentException("Candidates must contain unique persisted users");
            }
            try {
                ZoneId zoneId = timeZoneSupport.zoneId(user);
                LocalDate localDate = evaluatedAt.atZone(zoneId).toLocalDate();
                byLocalDate.computeIfAbsent(localDate, ignored -> new ArrayList<>())
                        .add(new CandidateContext(user, zoneId, localDate));
            } catch (IllegalArgumentException ex) {
                invalidTimeZones.add(new CandidateContext(user, null, null));
            }
        }

        List<DailyMealReminderSnapshot> result = new ArrayList<>(candidates.size());
        invalidTimeZones.forEach(context -> result.add(unavailableSnapshot(context, evaluatedAt)));
        byLocalDate.forEach((localDate, contexts) -> result.addAll(buildLocalDateBatch(contexts, evaluatedAt)));
        Map<Long, DailyMealReminderSnapshot> byUserId = new LinkedHashMap<>();
        result.forEach(snapshot -> byUserId.put(snapshot.userId(), snapshot));
        return candidates.stream().map(user -> byUserId.get(user.getId())).toList();
    }

    private List<DailyMealReminderSnapshot> buildLocalDateBatch(List<CandidateContext> contexts, Instant evaluatedAt) {
        List<UserEntity> users = contexts.stream().map(CandidateContext::user).toList();
        List<Long> userIds = users.stream().map(UserEntity::getId).toList();
        LocalDate localDate = contexts.get(0).localDate();
        LocalDateTime start = localDate.atStartOfDay();
        LocalDateTime end = localDate.plusDays(1).atStartOfDay();

        AggregateRead food = readAggregates(() -> foodLogsRepository
                .aggregateMealReminderByUsersAndDate(userIds, start, end));
        AggregateRead recipe = readAggregates(() -> recipeLogRepository
                .aggregateMealReminderByUsersAndDate(userIds, start, end));
        UnresolvedPlanRead unresolvedPlan = readUnresolvedPlan(userIds, localDate);

        Map<Long, DailyCalorieBudgetSupport.ResolvedGoal> goals;
        boolean goalsReliable = true;
        try {
            goals = DailyCalorieBudgetSupport.resolveGoals(goalRepository, users, localDate);
        } catch (RuntimeException ex) {
            goals = Map.of();
            goalsReliable = false;
        }

        Set<Long> fastingUsers;
        boolean fastingReliable = true;
        try {
            fastingUsers = new HashSet<>(fastingSessionRepository.findDistinctUserIdsByStatus(
                    userIds, FastingSessionStatus.ACTIVE));
        } catch (RuntimeException ex) {
            fastingUsers = Set.of();
            fastingReliable = false;
        }

        List<DailyMealReminderSnapshot> snapshots = new ArrayList<>(contexts.size());
        for (CandidateContext context : contexts) {
            boolean aggregateReliable = food.reliable() && recipe.reliable();
            Set<MealReminderContract.Meal> unresolvedMeals = unresolvedPlan.byUser()
                    .getOrDefault(context.user().getId(), Set.of());
            Map<MealReminderContract.Meal, DailyMealReminderSnapshot.MealTotals> totals = mealTotals(
                    context.user().getId(), food.rows(), recipe.rows());
            Map<MealReminderContract.Meal, MealReminderContract.MealState> states = mealStates(
                    totals, aggregateReliable && unresolvedPlan.reliable(), unresolvedMeals);
            DailyCalorieBudgetSupport.ResolvedGoal resolvedGoal = goals.get(context.user().getId());
            UserGoalEntity goal = resolvedGoal == null ? null : resolvedGoal.goal();
            boolean calorieReliable = aggregateReliable
                    && unresolvedPlan.reliable()
                    && unresolvedMeals.isEmpty()
                    && goalsReliable
                    && resolvedGoal != null;
            DailyCalorieBudgetSupport.DailyCalorieBudget budget = calorieReliable
                    ? DailyCalorieBudgetSupport.calculate(
                            resolvedGoal,
                            totalCalories(totals, true),
                            totalCalories(totals, false))
                    : null;
            snapshots.add(new DailyMealReminderSnapshot(
                    context.user().getId(),
                    evaluatedAt,
                    context.localDate(),
                    context.zoneId().getId(),
                    goal == null ? null : goal.getId(),
                    goal == null ? null : goal.getVersion(),
                    resolvedGoal == null ? "UNKNOWN" : resolvedGoal.source().name(),
                    goal == null || goal.getCalculationMode() == null ? null : goal.getCalculationMode().name(),
                    budget == null ? null : budget.targetCalories(),
                    budget == null ? null : budget.foodCalories(),
                    budget == null ? null : budget.recipeCalories(),
                    budget == null ? null : budget.consumedCalories(),
                    budget == null ? null : budget.remainingCalories(),
                    calorieReliable,
                    budget != null && budget.targetValid(),
                    totals,
                    states,
                    fastingReliable
                            ? (fastingUsers.contains(context.user().getId())
                                    ? DailyMealReminderSnapshot.ActivityState.ACTIVE
                                    : DailyMealReminderSnapshot.ActivityState.INACTIVE)
                            : DailyMealReminderSnapshot.ActivityState.UNKNOWN,
                    Boolean.TRUE.equals(context.user().getPushNotificationsEnabled()),
                    Boolean.TRUE.equals(context.user().getMealRemindersEnabled()),
                    context.user().getNotificationQuietHoursStart(),
                    context.user().getNotificationQuietHoursEnd()
            ));
        }
        return snapshots;
    }

    private DailyMealReminderSnapshot unavailableSnapshot(CandidateContext context, Instant evaluatedAt) {
        Map<MealReminderContract.Meal, DailyMealReminderSnapshot.MealTotals> totals = emptyTotals();
        Map<MealReminderContract.Meal, MealReminderContract.MealState> states = mealStates(totals, false, Set.of());
        UserEntity user = context.user();
        return new DailyMealReminderSnapshot(
                user.getId(), evaluatedAt, null, user.getTimeZone(), null, null, "UNKNOWN", null,
                null, null, null, null, null, false, false, totals, states,
                DailyMealReminderSnapshot.ActivityState.UNKNOWN,
                Boolean.TRUE.equals(user.getPushNotificationsEnabled()),
                Boolean.TRUE.equals(user.getMealRemindersEnabled()),
                user.getNotificationQuietHoursStart(), user.getNotificationQuietHoursEnd());
    }

    private AggregateRead readAggregates(AggregateSupplier supplier) {
        try {
            return new AggregateRead(index(supplier.get()), true);
        } catch (RuntimeException ex) {
            return new AggregateRead(Map.of(), false);
        }
    }

    private UnresolvedPlanRead readUnresolvedPlan(List<Long> userIds, LocalDate localDate) {
        try {
            Map<Long, Set<MealReminderContract.Meal>> byUser = new LinkedHashMap<>();
            List<MealPlanItemConsumptionStatus> linkedStatuses = List.of(
                    MealPlanItemConsumptionStatus.LOGGED,
                    MealPlanItemConsumptionStatus.PARTIALLY_CONSUMED,
                    MealPlanItemConsumptionStatus.REPLACED);
            for (MealReminderUnresolvedPlanProjection row : mealPlanItemConsumptionRepository
                    .findUnresolvedReminderConsumptions(userIds, localDate, linkedStatuses)) {
                MealReminderContract.Meal meal = parseMeal(row.getMealType());
                if (row.getUserId() != null && meal != null) {
                    byUser.computeIfAbsent(row.getUserId(), ignored -> new HashSet<>()).add(meal);
                }
            }
            return new UnresolvedPlanRead(byUser, true);
        } catch (RuntimeException ex) {
            return new UnresolvedPlanRead(Map.of(), false);
        }
    }

    private Map<AggregateKey, AggregateValue> index(List<MealReminderMealAggregateProjection> rows) {
        Map<AggregateKey, AggregateValue> result = new LinkedHashMap<>();
        for (MealReminderMealAggregateProjection row : rows) {
            MealReminderContract.Meal meal = parseMeal(row.getMealType());
            if (meal != null) {
                result.put(new AggregateKey(row.getUserId(), meal), new AggregateValue(
                        row.getRecordCount() == null ? 0L : row.getRecordCount(),
                        row.getCalories() == null ? 0.0 : row.getCalories()));
            }
        }
        return result;
    }

    private Map<MealReminderContract.Meal, DailyMealReminderSnapshot.MealTotals> mealTotals(
            Long userId,
            Map<AggregateKey, AggregateValue> food,
            Map<AggregateKey, AggregateValue> recipe
    ) {
        Map<MealReminderContract.Meal, DailyMealReminderSnapshot.MealTotals> result = new EnumMap<>(MealReminderContract.Meal.class);
        for (MealReminderContract.Meal meal : MealReminderContract.Meal.values()) {
            AggregateValue foodValue = food.getOrDefault(new AggregateKey(userId, meal), AggregateValue.ZERO);
            AggregateValue recipeValue = recipe.getOrDefault(new AggregateKey(userId, meal), AggregateValue.ZERO);
            result.put(meal, new DailyMealReminderSnapshot.MealTotals(
                    foodValue.count(), recipeValue.count(),
                    DailyCalorieBudgetSupport.round(foodValue.calories()),
                    DailyCalorieBudgetSupport.round(recipeValue.calories())));
        }
        return result;
    }

    private Map<MealReminderContract.Meal, DailyMealReminderSnapshot.MealTotals> emptyTotals() {
        return mealTotals(-1L, Map.of(), Map.of());
    }

    private Map<MealReminderContract.Meal, MealReminderContract.MealState> mealStates(
            Map<MealReminderContract.Meal, DailyMealReminderSnapshot.MealTotals> totals,
            boolean reliable,
            Set<MealReminderContract.Meal> unresolvedMeals
    ) {
        Map<MealReminderContract.Meal, MealReminderContract.MealState> states = new EnumMap<>(MealReminderContract.Meal.class);
        for (MealReminderContract.Meal meal : MealReminderContract.Meal.values()) {
            DailyMealReminderSnapshot.MealTotals mealTotal = totals.get(meal);
            states.put(meal, MealReminderContract.classify(new MealReminderContract.MealEvidence(
                    reliable && !unresolvedMeals.contains(meal),
                    mealTotal == null ? 0 : Math.toIntExact(mealTotal.foodRecords()),
                    mealTotal == null ? 0 : Math.toIntExact(mealTotal.recipeRecords()),
                    false
            )));
        }
        return states;
    }

    private double totalCalories(
            Map<MealReminderContract.Meal, DailyMealReminderSnapshot.MealTotals> totals,
            boolean food
    ) {
        return totals.values().stream()
                .mapToDouble(total -> food ? total.foodCalories() : total.recipeCalories())
                .sum();
    }

    private MealReminderContract.Meal parseMeal(String value) {
        if (value == null) {
            return null;
        }
        try {
            return MealReminderContract.Meal.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private record CandidateContext(UserEntity user, ZoneId zoneId, LocalDate localDate) {
    }

    private record AggregateKey(Long userId, MealReminderContract.Meal meal) {
    }

    private record AggregateValue(long count, double calories) {
        private static final AggregateValue ZERO = new AggregateValue(0, 0.0);
    }

    private record AggregateRead(Map<AggregateKey, AggregateValue> rows, boolean reliable) {
    }

    private record UnresolvedPlanRead(
            Map<Long, Set<MealReminderContract.Meal>> byUser,
            boolean reliable
    ) {
    }

    @FunctionalInterface
    private interface AggregateSupplier {
        List<MealReminderMealAggregateProjection> get();
    }
}
