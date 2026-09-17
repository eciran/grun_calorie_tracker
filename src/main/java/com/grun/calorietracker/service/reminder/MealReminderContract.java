package com.grun.calorietracker.service.reminder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Versioned, side-effect-free vocabulary and defaults for MR-01.
 * Not a Spring bean, scheduler, preference migration, or delivery service.
 * MR-02 supplies canonical evidence; MR-03 implements the decision engine.
 */
public final class MealReminderContract {
    public static final String VERSION = "meal_reminder_v1";
    public static final Mode DEFAULT_MODE = Mode.OFF;
    public static final boolean DELIVERY_ENABLED_BY_DEFAULT = false;
    public static final String DEFAULT_CHANNEL = "IN_APP_AND_PUSH";
    public static final String RETENTION_POLICY_KEY = "NOTIFICATIONS";
    public static final int MAX_DAILY_OCCURRENCES = 3;
    public static final int MAX_ROLLING_24H_OCCURRENCES = 3;
    public static final int MAX_DAILY_CATCHUPS = 1;
    public static final Duration MIN_MEAL_REMINDER_GAP = Duration.ofHours(3);
    public static final Duration ROUTINE_REMINDER_GAP = Duration.ofMinutes(30);
    public static final Duration MAX_SLOT_AGE = Duration.ofHours(1);
    public static final Duration SCAN_INTERVAL = Duration.ofMinutes(5);
    public static final LocalTime DEFAULT_QUIET_START = LocalTime.of(22, 0);
    public static final LocalTime DEFAULT_QUIET_END = LocalTime.of(8, 0);
    public static final List<Meal> MAIN_MEALS = List.of(Meal.BREAKFAST, Meal.LUNCH, Meal.DINNER);
    public static final Map<Meal, LocalTime> DEFAULT_TIMES = Map.of(
            Meal.BREAKFAST, LocalTime.of(10, 0),
            Meal.LUNCH, LocalTime.of(14, 30),
            Meal.DINNER, LocalTime.of(20, 30));

    private MealReminderContract() { }

    public enum Mode { OFF, DRY_RUN, PILOT, LIVE }
    public enum Meal { BREAKFAST, LUNCH, DINNER, SNACK }
    public enum MealState { RECORDED, MISSING, EXCLUDED, UNKNOWN }

    /** DAILY_CATCHUP and DINNER share the EVENING occurrence identity. */
    public enum Slot { BREAKFAST, LUNCH, EVENING }

    public enum Reason {
        ELIGIBLE, SYSTEM_DISABLED, ACCOUNT_INELIGIBLE, PREFERENCE_DISABLED,
        OUTSIDE_COHORT, NO_VALID_TOKEN, DATA_UNAVAILABLE, QUIET_HOURS,
        FASTING_ACTIVE, NOT_DUE, STALE_SLOT, ALREADY_HANDLED, DAILY_LIMIT,
        ROLLING_LIMIT, COOLDOWN, ROUTINE_COOLDOWN, PREVIOUS_MEAL_MISSING,
        MEAL_ALREADY_RECORDED, MEAL_EXCLUDED, NO_DIARY_ACTIVITY, DAY_COMPLETE,
        CATCHUP_LIMIT
    }

    /** Kcal suppression is separate from suppression of the notification itself. */
    public enum KcalReason {
        SHOWN, NOT_DINNER, DISABLED, PREVIOUS_MEAL_MISSING, NO_DIARY_ACTIVITY,
        INVALID_TARGET, DATA_UNAVAILABLE, NON_POSITIVE_REMAINDER,
        ROUNDS_TO_ZERO, UNRESOLVED_PLAN
    }

    /** Failure priority is part of the contract, not inferred from enum order. */
    public static final List<Reason> SUPPRESSION_PRIORITY = List.of(
            Reason.SYSTEM_DISABLED, Reason.ACCOUNT_INELIGIBLE, Reason.PREFERENCE_DISABLED,
            Reason.OUTSIDE_COHORT, Reason.NO_VALID_TOKEN, Reason.DATA_UNAVAILABLE,
            Reason.FASTING_ACTIVE, Reason.NOT_DUE, Reason.STALE_SLOT,
            Reason.ALREADY_HANDLED, Reason.DAILY_LIMIT, Reason.ROLLING_LIMIT,
            Reason.COOLDOWN, Reason.ROUTINE_COOLDOWN);

    /** Counts are canonical diary records, never planned quantities or snapshot kcal. */
    public record MealEvidence(boolean reliable, int foodRecordCount, int recipeRecordCount,
                               boolean explicitWholeMealExclusion) {
        public MealEvidence {
            if (foodRecordCount < 0 || recipeRecordCount < 0) {
                throw new IllegalArgumentException("Diary record counts cannot be negative");
            }
        }
    }

    public static MealState classify(MealEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        if (!evidence.reliable()) return MealState.UNKNOWN;
        if (evidence.foodRecordCount() > 0 || evidence.recipeRecordCount() > 0) {
            return MealState.RECORDED;
        }
        return evidence.explicitWholeMealExclusion() ? MealState.EXCLUDED : MealState.MISSING;
    }

    public static List<Meal> precedingMeals(Meal meal) {
        Objects.requireNonNull(meal, "meal");
        return switch (meal) {
            case BREAKFAST -> List.of();
            case LUNCH -> List.of(Meal.BREAKFAST);
            case DINNER -> List.of(Meal.BREAKFAST, Meal.LUNCH);
            case SNACK -> throw new IllegalArgumentException("SNACK has no mandatory reminder slot");
        };
    }

    public enum Message {
        BREAKFAST("meal_reminder_breakfast", Slot.BREAKFAST),
        LUNCH("meal_reminder_lunch", Slot.LUNCH),
        DINNER("meal_reminder_dinner", Slot.EVENING),
        DINNER_KCAL("meal_reminder_dinner_kcal", Slot.EVENING),
        DAILY_CATCHUP("meal_reminder_daily_catchup", Slot.EVENING);

        private final String definitionKey;
        private final Slot slot;

        Message(String definitionKey, Slot slot) {
            this.definitionKey = definitionKey;
            this.slot = slot;
        }

        public String definitionKey() { return definitionKey; }
        public Slot slot() { return slot; }
        public Set<String> allowedParameters() {
            return this == DINNER_KCAL ? Set.of("remainingKcal") : Set.of();
        }
    }

    public record Copy(String title, String body) { }

    /** Initial seed copy only. MR-05 must store/edit published copy in Notification Definitions. */
    public static Copy initialCopy(Message message, String language) {
        Objects.requireNonNull(message, "message");
        String tag = language == null ? "" : language.trim().replace('_', '-');
        boolean turkish = "tr".equals(Locale.forLanguageTag(tag).getLanguage());
        if (turkish) {
            return switch (message) {
                case BREAKFAST -> new Copy("Kahvaltı günlüğün", "Kahvaltını günlüğüne eklemek ister misin?");
                case LUNCH -> new Copy("Öğle öğünün", "Öğle öğünün henüz günlüğünde görünmüyor. Vaktin olduğunda ekleyebilirsin.");
                case DINNER -> new Copy("Akşam öğünün", "Akşam öğününü de günlüğüne eklemek ister misin?");
                case DINNER_KCAL -> new Copy("Akşam öğünün", "Kaydettiğin öğünlere göre günlük hedefinde yaklaşık {remainingKcal} kcal kalıyor. Akşam öğününü eklemek ister misin?");
                case DAILY_CATCHUP -> new Copy("Bugünkü günlüğün", "Bugünkü günlüğünde eksik öğünler olabilir. Vaktin olduğunda tamamlamak ister misin?");
            };
        }
        return switch (message) {
            case BREAKFAST -> new Copy("Your breakfast diary", "Would you like to add your breakfast to your diary?");
            case LUNCH -> new Copy("Your lunch diary", "Your lunch isn't in your diary yet. You can add it when you have a moment.");
            case DINNER -> new Copy("Your dinner diary", "Would you like to add your dinner to your diary too?");
            case DINNER_KCAL -> new Copy("Your dinner diary", "Based on your logged meals, about {remainingKcal} kcal remains against your daily target. Would you like to log your dinner?");
            case DAILY_CATCHUP -> new Copy("Today's diary", "There may be meals missing from today's diary. Would you like to complete it when you have a moment?");
        };
    }

    /** Eligibility is decided by MR-03; this formatter never computes a food budget. */
    public static Copy renderInitialCopy(Message message, String language, BigDecimal remainingKcal) {
        Copy copy = initialCopy(message, language);
        if (message != Message.DINNER_KCAL) return copy;
        if (remainingKcal == null || remainingKcal.signum() <= 0) {
            throw new IllegalArgumentException("A positive canonical remainder is required");
        }
        BigDecimal rounded = remainingKcal.setScale(0, RoundingMode.HALF_UP);
        if (rounded.signum() == 0) {
            throw new IllegalArgumentException("Use neutral dinner copy when the remainder rounds to zero");
        }
        return new Copy(copy.title(), copy.body().replace("{remainingKcal}", rounded.toPlainString()));
    }
}
