package com.grun.calorietracker.service.reminder;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;
import java.util.Objects;

public record MealReminderPolicy(
        String version,
        MealReminderContract.Mode mode,
        boolean deploymentGateEnabled,
        boolean kcalEnabled,
        Map<MealReminderContract.Meal, LocalTime> mealTimes,
        Duration slotAge,
        int maxDailyOccurrences,
        int maxRollingOccurrences,
        int maxDailyCatchups,
        Duration minimumReminderGap,
        Duration routineReminderGap,
        LocalTime defaultQuietStart,
        LocalTime defaultQuietEnd
) {
    public MealReminderPolicy {
        if (version == null || version.isBlank() || version.length() > 80) {
            throw new IllegalArgumentException("A policy version of at most 80 characters is required");
        }
        Objects.requireNonNull(mode, "mode");
        mealTimes = Map.copyOf(Objects.requireNonNull(mealTimes, "mealTimes"));
        for (MealReminderContract.Meal meal : MealReminderContract.MAIN_MEALS) {
            Objects.requireNonNull(mealTimes.get(meal), "Missing time for " + meal);
        }
        if (!mealTimes.get(MealReminderContract.Meal.BREAKFAST)
                .isBefore(mealTimes.get(MealReminderContract.Meal.LUNCH))
                || !mealTimes.get(MealReminderContract.Meal.LUNCH)
                .isBefore(mealTimes.get(MealReminderContract.Meal.DINNER))) {
            throw new IllegalArgumentException("Meal times must be strictly ordered");
        }
        if (slotAge == null || slotAge.isZero() || slotAge.isNegative()
                || slotAge.compareTo(MealReminderContract.MAX_SLOT_AGE) > 0) {
            throw new IllegalArgumentException("Slot age must be between 1 minute and 60 minutes");
        }
        if (maxDailyOccurrences < 0 || maxDailyOccurrences > MealReminderContract.MAX_DAILY_OCCURRENCES
                || maxRollingOccurrences < 0 || maxRollingOccurrences > MealReminderContract.MAX_ROLLING_24H_OCCURRENCES
                || maxDailyCatchups < 0 || maxDailyCatchups > MealReminderContract.MAX_DAILY_CATCHUPS) {
            throw new IllegalArgumentException("Reminder limits exceed the V1 safety bounds");
        }
        if (minimumReminderGap == null
                || minimumReminderGap.compareTo(MealReminderContract.MIN_MEAL_REMINDER_GAP) < 0
                || routineReminderGap == null
                || routineReminderGap.compareTo(MealReminderContract.ROUTINE_REMINDER_GAP) < 0) {
            throw new IllegalArgumentException("Reminder cooldowns are below the V1 safety bounds");
        }
        Objects.requireNonNull(defaultQuietStart, "defaultQuietStart");
        Objects.requireNonNull(defaultQuietEnd, "defaultQuietEnd");
    }

    public static MealReminderPolicy dryRunDefaults(String version) {
        return new MealReminderPolicy(
                version,
                MealReminderContract.Mode.DRY_RUN,
                false,
                true,
                MealReminderContract.DEFAULT_TIMES,
                MealReminderContract.MAX_SLOT_AGE,
                MealReminderContract.MAX_DAILY_OCCURRENCES,
                MealReminderContract.MAX_ROLLING_24H_OCCURRENCES,
                MealReminderContract.MAX_DAILY_CATCHUPS,
                MealReminderContract.MIN_MEAL_REMINDER_GAP,
                MealReminderContract.ROUTINE_REMINDER_GAP,
                MealReminderContract.DEFAULT_QUIET_START,
                MealReminderContract.DEFAULT_QUIET_END);
    }

    public LocalTime timeFor(MealReminderContract.Slot slot) {
        return switch (slot) {
            case BREAKFAST -> mealTimes.get(MealReminderContract.Meal.BREAKFAST);
            case LUNCH -> mealTimes.get(MealReminderContract.Meal.LUNCH);
            case EVENING -> mealTimes.get(MealReminderContract.Meal.DINNER);
        };
    }
}
