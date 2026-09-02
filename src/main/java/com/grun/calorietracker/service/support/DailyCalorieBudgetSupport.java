package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.repository.GoalRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Canonical effective-goal and calorie arithmetic shared by dashboard and reminders. */
public final class DailyCalorieBudgetSupport {

    private DailyCalorieBudgetSupport() {
    }

    public static ResolvedGoal resolveGoal(GoalRepository repository, UserEntity user, LocalDate localDate) {
        Optional<UserGoalEntity> effective = repository
                .findFirstByUserAndEffectiveLocalDateLessThanEqualOrderByEffectiveFromDesc(user, localDate);
        if (effective.isPresent()) {
            return new ResolvedGoal(effective.get(), GoalSource.EFFECTIVE_DATE);
        }
        return repository.findByUser(user)
                .map(goal -> new ResolvedGoal(goal, GoalSource.CURRENT_FALLBACK))
                .orElseGet(() -> new ResolvedGoal(null, GoalSource.NONE));
    }

    public static Map<Long, ResolvedGoal> resolveGoals(
            GoalRepository repository,
            List<UserEntity> users,
            LocalDate localDate
    ) {
        List<Long> userIds = users.stream().map(UserEntity::getId).distinct().toList();
        Map<Long, List<UserGoalEntity>> byUser = new LinkedHashMap<>();
        for (UserGoalEntity goal : repository.findAllByUserIdIn(userIds)) {
            if (goal.getUser() != null && goal.getUser().getId() != null) {
                byUser.computeIfAbsent(goal.getUser().getId(), ignored -> new ArrayList<>()).add(goal);
            }
        }

        Map<Long, ResolvedGoal> result = new LinkedHashMap<>();
        for (UserEntity user : users) {
            List<UserGoalEntity> goals = byUser.getOrDefault(user.getId(), List.of());
            Optional<UserGoalEntity> dated = goals.stream()
                    .filter(goal -> goal.getEffectiveLocalDate() != null
                            && !goal.getEffectiveLocalDate().isAfter(localDate))
                    .max(Comparator.comparing(DailyCalorieBudgetSupport::effectiveFrom));
            if (dated.isPresent()) {
                result.put(user.getId(), new ResolvedGoal(dated.get(), GoalSource.EFFECTIVE_DATE));
                continue;
            }
            Optional<UserGoalEntity> current = goals.stream()
                    .filter(goal -> goal.getEffectiveUntil() == null)
                    .max(Comparator.comparing(DailyCalorieBudgetSupport::effectiveFrom));
            result.put(user.getId(), current
                    .map(goal -> new ResolvedGoal(goal, GoalSource.CURRENT_FALLBACK))
                    .orElseGet(() -> new ResolvedGoal(null, GoalSource.NONE)));
        }
        return result;
    }

    public static DailyCalorieBudget calculate(ResolvedGoal resolvedGoal, double foodCalories, double recipeCalories) {
        UserGoalEntity goal = resolvedGoal == null ? null : resolvedGoal.goal();
        int targetCalories = goal == null || goal.getDailyCalorieGoal() == null ? 0 : goal.getDailyCalorieGoal();
        double roundedFood = round(foodCalories);
        double roundedRecipe = round(recipeCalories);
        double consumed = round(roundedFood + roundedRecipe);
        return new DailyCalorieBudget(
                resolvedGoal == null ? new ResolvedGoal(null, GoalSource.NONE) : resolvedGoal,
                targetCalories,
                roundedFood,
                roundedRecipe,
                consumed,
                round(targetCalories - consumed),
                goal != null && targetCalories > 0
        );
    }

    public static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static LocalDateTime effectiveFrom(UserGoalEntity goal) {
        return goal.getEffectiveFrom() == null ? LocalDateTime.MIN : goal.getEffectiveFrom();
    }

    public enum GoalSource {
        EFFECTIVE_DATE,
        CURRENT_FALLBACK,
        NONE
    }

    public record ResolvedGoal(UserGoalEntity goal, GoalSource source) {
    }

    public record DailyCalorieBudget(
            ResolvedGoal resolvedGoal,
            int targetCalories,
            double foodCalories,
            double recipeCalories,
            double consumedCalories,
            double remainingCalories,
            boolean targetValid
    ) {
    }
}
