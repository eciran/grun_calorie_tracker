package com.grun.calorietracker.service.reminder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

public record DailyMealReminderSnapshot(
        Long userId,
        Instant evaluatedAt,
        LocalDate localDate,
        String timeZone,
        Long goalId,
        Long goalVersion,
        String goalResolutionSource,
        String goalCalculationMode,
        Integer targetCalories,
        Double foodCalories,
        Double recipeCalories,
        Double consumedCalories,
        Double remainingCalories,
        boolean calorieDataReliable,
        boolean targetValid,
        Map<MealReminderContract.Meal, MealTotals> mealTotals,
        Map<MealReminderContract.Meal, MealReminderContract.MealState> mealStates,
        ActivityState fastingState,
        boolean pushNotificationsEnabled,
        boolean mealRemindersEnabled,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd
) {
    public DailyMealReminderSnapshot {
        mealTotals = Map.copyOf(mealTotals);
        mealStates = Map.copyOf(mealStates);
    }

    public enum ActivityState {
        ACTIVE,
        INACTIVE,
        UNKNOWN
    }

    public record MealTotals(long foodRecords, long recipeRecords, double foodCalories, double recipeCalories) {
        public long recordCount() {
            return foodRecords + recipeRecords;
        }

        public double calories() {
            return foodCalories + recipeCalories;
        }
    }
}
