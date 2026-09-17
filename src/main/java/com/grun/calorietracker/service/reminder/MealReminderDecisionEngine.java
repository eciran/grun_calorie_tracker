package com.grun.calorietracker.service.reminder;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class MealReminderDecisionEngine {

    private static final Duration MAX_SNAPSHOT_AGE = MealReminderContract.SCAN_INTERVAL.multipliedBy(2);

    private final Clock clock;

    public MealReminderDecisionEngine(Clock analyticsClock) {
        this.clock = Objects.requireNonNull(analyticsClock, "analyticsClock");
    }

    public MealReminderDecision evaluate(
            DailyMealReminderSnapshot snapshot,
            MealReminderPolicy policy,
            MealReminderRuntimeState runtime
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(runtime, "runtime");
        Instant now = clock.instant();

        ZoneId zoneId;
        LocalDate localDate;
        LocalTime localTime;
        try {
            zoneId = ZoneId.of(snapshot.timeZone());
            localDate = now.atZone(zoneId).toLocalDate();
            localTime = now.atZone(zoneId).toLocalTime();
        } catch (RuntimeException ex) {
            return suppressed(snapshot, policy, now, null, null, MealReminderContract.Reason.DATA_UNAVAILABLE);
        }

        SlotWindow requestedWindow = runtime.requestedSlot() == null
                ? null
                : window(runtime.requestedSlot(), localDate, zoneId, policy).orElse(null);
        SlotWindow activeWindow = requestedWindow != null
                ? requestedWindow
                : activeWindow(now, localDate, zoneId, policy).orElse(null);

        if (policy.mode() == MealReminderContract.Mode.OFF
                || ((policy.mode() == MealReminderContract.Mode.PILOT
                || policy.mode() == MealReminderContract.Mode.LIVE) && !policy.deploymentGateEnabled())) {
            return suppressed(snapshot, policy, now, activeWindow, null, MealReminderContract.Reason.SYSTEM_DISABLED);
        }
        if (!runtime.accountEligible()) {
            return suppressed(snapshot, policy, now, activeWindow, null, MealReminderContract.Reason.ACCOUNT_INELIGIBLE);
        }
        if (!snapshot.pushNotificationsEnabled() || !snapshot.mealRemindersEnabled()) {
            return suppressed(snapshot, policy, now, activeWindow, null, MealReminderContract.Reason.PREFERENCE_DISABLED);
        }
        if (policy.mode() == MealReminderContract.Mode.PILOT && !runtime.inPilotCohort()) {
            return suppressed(snapshot, policy, now, activeWindow, null, MealReminderContract.Reason.OUTSIDE_COHORT);
        }
        if (!runtime.hasValidPushToken()) {
            return suppressed(snapshot, policy, now, activeWindow, null, MealReminderContract.Reason.NO_VALID_TOKEN);
        }
        if (dataUnavailable(snapshot, now, localDate)) {
            return suppressed(snapshot, policy, now, activeWindow, null, MealReminderContract.Reason.DATA_UNAVAILABLE);
        }

        if (snapshot.fastingState() == DailyMealReminderSnapshot.ActivityState.ACTIVE) {
            return suppressed(snapshot, policy, now, activeWindow, null, MealReminderContract.Reason.FASTING_ACTIVE);
        }

        if (runtime.requestedSlot() != null && requestedWindow == null) {
            return suppressed(snapshot, policy, now, null, null, MealReminderContract.Reason.STALE_SLOT);
        }
        if (activeWindow == null) {
            return suppressed(snapshot, policy, now, null, null, MealReminderContract.Reason.NOT_DUE);
        }
        if (now.isBefore(activeWindow.eligibleAt())) {
            return suppressed(snapshot, policy, now, activeWindow, null, MealReminderContract.Reason.NOT_DUE);
        }
        if (!now.isBefore(activeWindow.expiresAt())) {
            return suppressed(snapshot, policy, now, activeWindow, null, MealReminderContract.Reason.STALE_SLOT);
        }
        if (runtime.handledSlots().contains(activeWindow.slot())) {
            return suppressed(snapshot, policy, now, activeWindow, null, MealReminderContract.Reason.ALREADY_HANDLED);
        }
        if (runtime.dailyOccurrences() >= policy.maxDailyOccurrences()) {
            return suppressed(snapshot, policy, now, activeWindow, null, MealReminderContract.Reason.DAILY_LIMIT);
        }
        if (runtime.rollingOccurrences() >= policy.maxRollingOccurrences()) {
            return suppressed(snapshot, policy, now, activeWindow, null, MealReminderContract.Reason.ROLLING_LIMIT);
        }
        if (withinGap(runtime.lastMealReminderAt(), now, policy.minimumReminderGap())) {
            return suppressed(snapshot, policy, now, activeWindow, null, MealReminderContract.Reason.COOLDOWN);
        }
        if (withinGap(runtime.lastRoutineReminderAt(), now, policy.routineReminderGap())) {
            return suppressed(snapshot, policy, now, activeWindow, null, MealReminderContract.Reason.ROUTINE_COOLDOWN);
        }

        return evaluateSlot(snapshot, policy, runtime, now, activeWindow);
    }

    private MealReminderDecision evaluateSlot(
            DailyMealReminderSnapshot snapshot,
            MealReminderPolicy policy,
            MealReminderRuntimeState runtime,
            Instant now,
            SlotWindow window
    ) {
        return switch (window.slot()) {
            case BREAKFAST -> evaluateMeal(snapshot, policy, runtime, now, window,
                    MealReminderContract.Meal.BREAKFAST, MealReminderContract.Message.BREAKFAST,
                    MealReminderDecision.Candidate.BREAKFAST);
            case LUNCH -> evaluateMeal(snapshot, policy, runtime, now, window,
                    MealReminderContract.Meal.LUNCH, MealReminderContract.Message.LUNCH,
                    MealReminderDecision.Candidate.LUNCH);
            case EVENING -> evaluateEvening(snapshot, policy, runtime, now, window);
        };
    }

    private MealReminderDecision evaluateMeal(
            DailyMealReminderSnapshot snapshot,
            MealReminderPolicy policy,
            MealReminderRuntimeState runtime,
            Instant now,
            SlotWindow window,
            MealReminderContract.Meal meal,
            MealReminderContract.Message message,
            MealReminderDecision.Candidate candidate
    ) {
        // Each meal is independently actionable; missing breakfast must not silence lunch.
        MealReminderContract.MealState state = snapshot.mealStates().get(meal);
        if (state == MealReminderContract.MealState.RECORDED) {
            return suppressed(snapshot, policy, now, window, candidate,
                    MealReminderContract.Reason.MEAL_ALREADY_RECORDED);
        }
        if (state == MealReminderContract.MealState.EXCLUDED) {
            return suppressed(snapshot, policy, now, window, candidate,
                    MealReminderContract.Reason.MEAL_EXCLUDED);
        }
        return send(snapshot, policy, runtime, now, window, candidate, message,
                MealReminderContract.Reason.ELIGIBLE, MealReminderContract.KcalReason.NOT_DINNER,
                Map.of(), null);
    }

    private MealReminderDecision evaluateEvening(
            DailyMealReminderSnapshot snapshot,
            MealReminderPolicy policy,
            MealReminderRuntimeState runtime,
            Instant now,
            SlotWindow window
    ) {
        boolean priorMissing = List.of(MealReminderContract.Meal.BREAKFAST, MealReminderContract.Meal.LUNCH)
                .stream().anyMatch(meal -> snapshot.mealStates().get(meal) == MealReminderContract.MealState.MISSING);
        boolean dayComplete = MealReminderContract.MAIN_MEALS.stream()
                .allMatch(meal -> {
                    MealReminderContract.MealState state = snapshot.mealStates().get(meal);
                    return state == MealReminderContract.MealState.RECORDED
                            || state == MealReminderContract.MealState.EXCLUDED;
                });
        if (dayComplete) {
            return suppressed(snapshot, policy, now, window, MealReminderDecision.Candidate.DINNER,
                    MealReminderContract.Reason.DAY_COMPLETE);
        }

        boolean hasDiaryActivity = snapshot.mealTotals().values().stream()
                .anyMatch(total -> total.recordCount() > 0);
        if (priorMissing) {
            if (runtime.dailyCatchups() >= policy.maxDailyCatchups()) {
                return suppressed(snapshot, policy, now, window, MealReminderDecision.Candidate.DAILY_CATCHUP,
                        MealReminderContract.Reason.CATCHUP_LIMIT);
            }
            MealReminderContract.Reason reason = hasDiaryActivity
                    ? MealReminderContract.Reason.PREVIOUS_MEAL_MISSING
                    : MealReminderContract.Reason.NO_DIARY_ACTIVITY;
            MealReminderContract.KcalReason kcalReason = hasDiaryActivity
                    ? MealReminderContract.KcalReason.PREVIOUS_MEAL_MISSING
                    : MealReminderContract.KcalReason.NO_DIARY_ACTIVITY;
            return send(snapshot, policy, runtime, now, window,
                    MealReminderDecision.Candidate.DAILY_CATCHUP,
                    MealReminderContract.Message.DAILY_CATCHUP,
                    reason, kcalReason, Map.of(), null);
        }

        MealReminderContract.MealState dinner = snapshot.mealStates().get(MealReminderContract.Meal.DINNER);
        if (dinner == MealReminderContract.MealState.RECORDED) {
            return suppressed(snapshot, policy, now, window, MealReminderDecision.Candidate.DINNER,
                    MealReminderContract.Reason.MEAL_ALREADY_RECORDED);
        }
        if (dinner == MealReminderContract.MealState.EXCLUDED) {
            return suppressed(snapshot, policy, now, window, MealReminderDecision.Candidate.DINNER,
                    MealReminderContract.Reason.MEAL_EXCLUDED);
        }

        KcalSelection kcal = selectKcal(snapshot, policy, hasDiaryActivity);
        MealReminderContract.Message message = kcal.reason() == MealReminderContract.KcalReason.SHOWN
                ? MealReminderContract.Message.DINNER_KCAL
                : MealReminderContract.Message.DINNER;
        Map<String, String> parameters = kcal.roundedValue() == null
                ? Map.of()
                : Map.of("remainingKcal", kcal.roundedValue());
        return send(snapshot, policy, runtime, now, window, MealReminderDecision.Candidate.DINNER,
                message, MealReminderContract.Reason.ELIGIBLE, kcal.reason(), parameters, kcal.remaining());
    }

    private KcalSelection selectKcal(
            DailyMealReminderSnapshot snapshot,
            MealReminderPolicy policy,
            boolean hasDiaryActivity
    ) {
        if (!policy.kcalEnabled()) {
            return new KcalSelection(MealReminderContract.KcalReason.DISABLED, null, null);
        }
        if (!hasDiaryActivity) {
            return new KcalSelection(MealReminderContract.KcalReason.NO_DIARY_ACTIVITY, null, null);
        }
        if (!snapshot.targetValid()) {
            return new KcalSelection(MealReminderContract.KcalReason.INVALID_TARGET, null, null);
        }
        if (!snapshot.calorieDataReliable() || snapshot.remainingCalories() == null) {
            return new KcalSelection(MealReminderContract.KcalReason.DATA_UNAVAILABLE, null, null);
        }
        BigDecimal remaining = BigDecimal.valueOf(snapshot.remainingCalories());
        if (remaining.signum() <= 0) {
            return new KcalSelection(MealReminderContract.KcalReason.NON_POSITIVE_REMAINDER, null, null);
        }
        BigDecimal rounded = remaining.setScale(0, RoundingMode.HALF_UP);
        if (rounded.signum() == 0) {
            return new KcalSelection(MealReminderContract.KcalReason.ROUNDS_TO_ZERO, null, null);
        }
        return new KcalSelection(MealReminderContract.KcalReason.SHOWN, rounded.toPlainString(), remaining);
    }

    private MealReminderDecision send(
            DailyMealReminderSnapshot snapshot,
            MealReminderPolicy policy,
            MealReminderRuntimeState runtime,
            Instant now,
            SlotWindow window,
            MealReminderDecision.Candidate candidate,
            MealReminderContract.Message message,
            MealReminderContract.Reason reason,
            MealReminderContract.KcalReason kcalReason,
            Map<String, String> parameters,
            BigDecimal remaining
    ) {
        if (!message.allowedParameters().equals(parameters.keySet())) {
            throw new IllegalStateException("Unsafe meal reminder parameters");
        }
        if (parameters.values().stream().anyMatch(value -> !value.matches("[0-9]{1,6}"))) {
            throw new IllegalStateException("Meal reminder parameters must be bounded integers");
        }
        MealReminderContract.Copy copy = MealReminderContract.renderInitialCopy(message, runtime.language(), remaining);
        return new MealReminderDecision(candidate, window.slot(), true, reason, kcalReason, message,
                parameters, copy, policy.version(), now, snapshot.localDate(),
                window.eligibleAt(), window.expiresAt());
    }

    private MealReminderDecision suppressed(
            DailyMealReminderSnapshot snapshot,
            MealReminderPolicy policy,
            Instant now,
            SlotWindow window,
            MealReminderDecision.Candidate candidate,
            MealReminderContract.Reason reason
    ) {
        return new MealReminderDecision(candidate, window == null ? null : window.slot(), false, reason,
                null, null, Map.of(), null, policy.version(), now, snapshot.localDate(),
                window == null ? null : window.eligibleAt(), window == null ? null : window.expiresAt());
    }

    private boolean dataUnavailable(DailyMealReminderSnapshot snapshot, Instant now, LocalDate localDate) {
        if (snapshot.localDate() == null || !snapshot.localDate().equals(localDate)
                || snapshot.evaluatedAt() == null) {
            return true;
        }
        Duration age = Duration.between(snapshot.evaluatedAt(), now);
        if (age.isNegative() || age.compareTo(MAX_SNAPSHOT_AGE) > 0) {
            return true;
        }
        if (snapshot.fastingState() == DailyMealReminderSnapshot.ActivityState.UNKNOWN) {
            return true;
        }
        return MealReminderContract.MAIN_MEALS.stream()
                .anyMatch(meal -> snapshot.mealStates().get(meal) == null
                        || snapshot.mealStates().get(meal) == MealReminderContract.MealState.UNKNOWN);
    }


    private Optional<SlotWindow> activeWindow(
            Instant now,
            LocalDate date,
            ZoneId zoneId,
            MealReminderPolicy policy
    ) {
        for (MealReminderContract.Slot slot : MealReminderContract.Slot.values()) {
            Optional<SlotWindow> window = window(slot, date, zoneId, policy);
            if (window.isPresent()
                    && !now.isBefore(window.get().eligibleAt())
                    && now.isBefore(window.get().expiresAt())) {
                return window;
            }
        }
        return Optional.empty();
    }

    private Optional<SlotWindow> window(
            MealReminderContract.Slot slot,
            LocalDate date,
            ZoneId zoneId,
            MealReminderPolicy policy
    ) {
        LocalDateTime startLocal = LocalDateTime.of(date, policy.timeFor(slot));
        LocalDateTime endLocal = startLocal.plus(policy.slotAge());
        Optional<Instant> start = resolveLocal(startLocal, zoneId);
        Optional<Instant> end = resolveLocal(endLocal, zoneId);
        if (start.isEmpty() || end.isEmpty() || !end.get().isAfter(start.get())) {
            return Optional.empty();
        }
        return Optional.of(new SlotWindow(slot, start.get(), end.get()));
    }

    private Optional<Instant> resolveLocal(LocalDateTime value, ZoneId zoneId) {
        ZoneRules rules = zoneId.getRules();
        List<ZoneOffset> offsets = rules.getValidOffsets(value);
        if (offsets.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(value.atOffset(offsets.get(0)).toInstant());
    }

    private boolean withinGap(Instant previous, Instant now, Duration gap) {
        if (previous == null) {
            return false;
        }
        Duration elapsed = Duration.between(previous, now);
        return elapsed.isNegative() || elapsed.compareTo(gap) < 0;
    }

    private record SlotWindow(MealReminderContract.Slot slot, Instant eligibleAt, Instant expiresAt) {
    }

    private record KcalSelection(
            MealReminderContract.KcalReason reason,
            String roundedValue,
            BigDecimal remaining
    ) {
    }

}
