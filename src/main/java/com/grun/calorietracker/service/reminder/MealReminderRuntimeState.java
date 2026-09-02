package com.grun.calorietracker.service.reminder;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record MealReminderRuntimeState(
        boolean accountEligible,
        boolean inPilotCohort,
        boolean hasValidPushToken,
        Set<MealReminderContract.Slot> handledSlots,
        int dailyOccurrences,
        int rollingOccurrences,
        int dailyCatchups,
        Instant lastMealReminderAt,
        Instant lastRoutineReminderAt,
        String language,
        MealReminderContract.Slot requestedSlot
) {
    public MealReminderRuntimeState {
        handledSlots = Set.copyOf(Objects.requireNonNull(handledSlots, "handledSlots"));
        if (dailyOccurrences < 0 || rollingOccurrences < 0 || dailyCatchups < 0) {
            throw new IllegalArgumentException("Reminder counters cannot be negative");
        }
    }

    public static MealReminderRuntimeState eligible(String language) {
        return new MealReminderRuntimeState(
                true, true, true, Set.of(), 0, 0, 0,
                null, null, language, null);
    }
}
