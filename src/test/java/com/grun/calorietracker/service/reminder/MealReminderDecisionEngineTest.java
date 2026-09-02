package com.grun.calorietracker.service.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MealReminderDecisionEngineTest {

    private static final JsonNode FIXTURES = loadFixtures();
    private static final LocalDate DATE = LocalDate.of(2026, 8, 29);

    @TestFactory
    Stream<DynamicTest> agreedDecisionFixturesExecuteAgainstRealEngine() {
        return StreamSupport.stream(FIXTURES.path("decisionCases").spliterator(), false)
                .map(row -> DynamicTest.dynamicTest(row.path("id").asText(), () -> {
                    FixtureInput input = fixtureInput(row);

                    MealReminderDecision decision = new MealReminderDecisionEngine(
                            Clock.fixed(input.now(), ZoneOffset.UTC))
                            .evaluate(input.snapshot(), input.policy(), input.runtime());

                    boolean expectedSend = !row.path("message").isNull();
                    assertEquals(expectedSend, decision.shouldSend());
                    assertEquals(MealReminderContract.Reason.valueOf(row.path("reason").asText()), decision.reason());
                    if (expectedSend) {
                        assertEquals(MealReminderContract.Message.valueOf(row.path("message").asText()), decision.message());
                        assertEquals(MealReminderContract.KcalReason.valueOf(row.path("kcalReason").asText()),
                                decision.kcalReason());
                        assertEquals(row.path("slot").asText(), decision.slot().name());
                    } else {
                        assertNull(decision.message());
                        assertNull(decision.kcalReason());
                        assertTrue(decision.safeParameters().isEmpty());
                    }
                }));
    }

    @Test
    void sameClockSnapshotPolicyAndRuntimeProduceIdenticalDecision() {
        Instant now = eveningInstant();
        DailyMealReminderSnapshot snapshot = snapshot(
                now,
                List.of(MealReminderContract.MealState.RECORDED,
                        MealReminderContract.MealState.RECORDED,
                        MealReminderContract.MealState.MISSING),
                650.50,
                true,
                true,
                true,
                DailyMealReminderSnapshot.ActivityState.INACTIVE,
                null,
                null);
        MealReminderDecisionEngine engine = new MealReminderDecisionEngine(Clock.fixed(now, ZoneOffset.UTC));
        MealReminderPolicy policy = MealReminderPolicy.dryRunDefaults("policy-v7");
        MealReminderRuntimeState runtime = MealReminderRuntimeState.eligible("tr-TR");

        MealReminderDecision first = engine.evaluate(snapshot, policy, runtime);
        MealReminderDecision second = engine.evaluate(snapshot, policy, runtime);

        assertEquals(first, second);
        assertEquals(Map.of("remainingKcal", "651"), first.safeParameters());
        assertEquals(Set.of("remainingKcal"), first.message().allowedParameters());
        assertTrue(first.renderedCopy().body().contains("651 kcal"));
    }

    @Test
    void unsupportedLanguageFallsBackToEnglishWithoutAddingParameters() {
        Instant now = breakfastInstant();
        DailyMealReminderSnapshot snapshot = snapshot(now,
                List.of(MealReminderContract.MealState.MISSING,
                        MealReminderContract.MealState.MISSING,
                        MealReminderContract.MealState.MISSING),
                null, true, true, true, DailyMealReminderSnapshot.ActivityState.INACTIVE,
                null, null);

        MealReminderDecision decision = new MealReminderDecisionEngine(Clock.fixed(now, ZoneOffset.UTC))
                .evaluate(snapshot, MealReminderPolicy.dryRunDefaults("policy-v1"),
                        MealReminderRuntimeState.eligible("fr-FR"));

        assertTrue(decision.shouldSend());
        assertEquals(MealReminderContract.Message.BREAKFAST, decision.message());
        assertEquals("Your breakfast diary", decision.renderedCopy().title());
        assertTrue(decision.safeParameters().isEmpty());
    }

    @Test
    void timezoneChangeOrStaleSnapshotFailsClosed() {
        Instant now = eveningInstant();
        DailyMealReminderSnapshot staleDate = new DailyMealReminderSnapshot(
                1L, now, DATE.minusDays(1), "Europe/Dublin", 1L, 1L,
                "EFFECTIVE_DATE", "AUTOMATIC", 2000, 600.0, 0.0, 600.0, 1400.0,
                true, true, totals(List.of(MealReminderContract.MealState.RECORDED,
                        MealReminderContract.MealState.RECORDED, MealReminderContract.MealState.MISSING)),
                states(List.of(MealReminderContract.MealState.RECORDED,
                        MealReminderContract.MealState.RECORDED, MealReminderContract.MealState.MISSING)),
                DailyMealReminderSnapshot.ActivityState.INACTIVE, true, true, null, null);

        MealReminderDecision decision = new MealReminderDecisionEngine(Clock.fixed(now, ZoneOffset.UTC))
                .evaluate(staleDate, MealReminderPolicy.dryRunDefaults("policy-v1"),
                        MealReminderRuntimeState.eligible("en"));

        assertFalse(decision.shouldSend());
        assertEquals(MealReminderContract.Reason.DATA_UNAVAILABLE, decision.reason());
    }

    @Test
    void missedSlotsDoNotCreateBacklogAndRequestedExpiredSlotIsStale() {
        Instant sixteenLocal = Instant.parse("2026-08-29T15:00:00Z");
        DailyMealReminderSnapshot snapshot = snapshot(sixteenLocal,
                List.of(MealReminderContract.MealState.MISSING,
                        MealReminderContract.MealState.MISSING,
                        MealReminderContract.MealState.MISSING),
                null, true, true, true, DailyMealReminderSnapshot.ActivityState.INACTIVE,
                null, null);
        MealReminderDecisionEngine engine = new MealReminderDecisionEngine(Clock.fixed(sixteenLocal, ZoneOffset.UTC));

        MealReminderDecision scan = engine.evaluate(snapshot, MealReminderPolicy.dryRunDefaults("policy-v1"),
                MealReminderRuntimeState.eligible("en"));
        MealReminderRuntimeState requestedLunch = new MealReminderRuntimeState(
                true, true, true, Set.of(), 0, 0, 0, null, null, "en",
                MealReminderContract.Slot.LUNCH);
        MealReminderDecision claimed = engine.evaluate(snapshot, MealReminderPolicy.dryRunDefaults("policy-v1"),
                requestedLunch);

        assertEquals(MealReminderContract.Reason.NOT_DUE, scan.reason());
        assertEquals(MealReminderContract.Reason.STALE_SLOT, claimed.reason());
        assertFalse(scan.shouldSend());
        assertFalse(claimed.shouldSend());
    }

    @Test
    void dstGapSlotIsSkippedInsteadOfShifted() {
        LocalDate dstDate = LocalDate.of(2026, 3, 29);
        Instant now = Instant.parse("2026-03-29T01:30:00Z");
        MealReminderPolicy policy = new MealReminderPolicy(
                "dst-policy", MealReminderContract.Mode.DRY_RUN, false, true,
                Map.of(
                        MealReminderContract.Meal.BREAKFAST, LocalTime.of(1, 30),
                        MealReminderContract.Meal.LUNCH, LocalTime.of(14, 30),
                        MealReminderContract.Meal.DINNER, LocalTime.of(20, 30)),
                Duration.ofMinutes(60), 3, 3, 1,
                Duration.ofMinutes(180), Duration.ofMinutes(30),
                LocalTime.of(22, 0), LocalTime.of(8, 0));
        DailyMealReminderSnapshot snapshot = snapshotForDate(now, dstDate,
                List.of(MealReminderContract.MealState.MISSING,
                        MealReminderContract.MealState.MISSING,
                        MealReminderContract.MealState.MISSING),
                null, true, true, true, DailyMealReminderSnapshot.ActivityState.INACTIVE,
                LocalTime.MIDNIGHT, LocalTime.MIDNIGHT);
        MealReminderRuntimeState runtime = new MealReminderRuntimeState(
                true, true, true, Set.of(), 0, 0, 0, null, null, "en",
                MealReminderContract.Slot.BREAKFAST);

        MealReminderDecision decision = new MealReminderDecisionEngine(Clock.fixed(now, ZoneOffset.UTC))
                .evaluate(snapshot, policy, runtime);

        assertEquals(MealReminderContract.Reason.STALE_SLOT, decision.reason());
        assertFalse(decision.shouldSend());
    }

    @Test
    void cooldownBoundariesAreInclusiveAndPolicyCannotRelaxSafetyMinimums() {
        Instant now = breakfastInstant();
        DailyMealReminderSnapshot snapshot = snapshot(now,
                List.of(MealReminderContract.MealState.MISSING,
                        MealReminderContract.MealState.MISSING,
                        MealReminderContract.MealState.MISSING),
                null, true, true, true, DailyMealReminderSnapshot.ActivityState.INACTIVE,
                null, null);
        MealReminderRuntimeState runtime = new MealReminderRuntimeState(
                true, true, true, Set.of(), 0, 0, 0,
                now.minus(Duration.ofMinutes(180)), now.minus(Duration.ofMinutes(30)), "en", null);

        MealReminderDecision decision = new MealReminderDecisionEngine(Clock.fixed(now, ZoneOffset.UTC))
                .evaluate(snapshot, MealReminderPolicy.dryRunDefaults("policy-v1"), runtime);

        assertTrue(decision.shouldSend());
        assertThrows(IllegalArgumentException.class, () -> new MealReminderPolicy(
                "unsafe", MealReminderContract.Mode.DRY_RUN, false, true,
                MealReminderContract.DEFAULT_TIMES, Duration.ofMinutes(61), 4, 3, 1,
                Duration.ofMinutes(179), Duration.ofMinutes(29),
                LocalTime.of(22, 0), LocalTime.of(8, 0)));
    }

    private FixtureInput fixtureInput(JsonNode row) {
        MealReminderContract.Slot fixtureSlot = MealReminderContract.Slot.valueOf(row.path("slot").asText());
        Set<MealReminderContract.Reason> conditions = new HashSet<>();
        row.path("conditions").forEach(value -> conditions.add(MealReminderContract.Reason.valueOf(value.asText())));
        Instant now = conditions.contains(MealReminderContract.Reason.NOT_DUE)
                ? Instant.parse("2026-08-29T08:15:00Z")
                : conditions.contains(MealReminderContract.Reason.STALE_SLOT)
                ? Instant.parse("2026-08-29T20:45:00Z")
                : instantFor(fixtureSlot);
        List<MealReminderContract.MealState> mealStates = StreamSupport
                .stream(row.path("states").spliterator(), false)
                .map(value -> MealReminderContract.MealState.valueOf(value.asText()))
                .toList();
        Double remaining = row.has("remainingKcal") ? row.path("remainingKcal").asDouble() : null;
        boolean preferenceEnabled = !conditions.contains(MealReminderContract.Reason.PREFERENCE_DISABLED);
        DailyMealReminderSnapshot.ActivityState fasting = conditions.contains(MealReminderContract.Reason.FASTING_ACTIVE)
                ? DailyMealReminderSnapshot.ActivityState.ACTIVE
                : DailyMealReminderSnapshot.ActivityState.INACTIVE;
        LocalTime quietStart = conditions.contains(MealReminderContract.Reason.QUIET_HOURS)
                ? LocalTime.of(20, 0) : null;
        LocalTime quietEnd = conditions.contains(MealReminderContract.Reason.QUIET_HOURS)
                ? LocalTime.of(22, 0) : null;
        boolean targetValid = !"INVALID_TARGET".equals(row.path("kcalReason").asText());
        DailyMealReminderSnapshot snapshot = snapshot(now, mealStates, remaining, true, targetValid,
                preferenceEnabled, fasting, quietStart, quietEnd);

        MealReminderContract.Mode mode = conditions.contains(MealReminderContract.Reason.SYSTEM_DISABLED)
                ? MealReminderContract.Mode.OFF
                : conditions.contains(MealReminderContract.Reason.OUTSIDE_COHORT)
                ? MealReminderContract.Mode.PILOT
                : MealReminderContract.Mode.DRY_RUN;
        boolean kcalEnabled = !"DISABLED".equals(row.path("kcalReason").asText());
        MealReminderPolicy policy = policy(mode, kcalEnabled);

        MealReminderRuntimeState runtime = new MealReminderRuntimeState(
                !conditions.contains(MealReminderContract.Reason.ACCOUNT_INELIGIBLE),
                !conditions.contains(MealReminderContract.Reason.OUTSIDE_COHORT),
                !conditions.contains(MealReminderContract.Reason.NO_VALID_TOKEN),
                conditions.contains(MealReminderContract.Reason.ALREADY_HANDLED)
                        ? Set.of(fixtureSlot) : Set.of(),
                conditions.contains(MealReminderContract.Reason.DAILY_LIMIT) ? 3 : 0,
                conditions.contains(MealReminderContract.Reason.ROLLING_LIMIT) ? 3 : 0,
                row.path("catchupsSent").asInt(0),
                conditions.contains(MealReminderContract.Reason.COOLDOWN) ? now.minusSeconds(60) : null,
                conditions.contains(MealReminderContract.Reason.ROUTINE_COOLDOWN) ? now.minusSeconds(60) : null,
                "tr",
                conditions.contains(MealReminderContract.Reason.STALE_SLOT) ? fixtureSlot : null);
        return new FixtureInput(now, snapshot, policy, runtime);
    }

    private MealReminderPolicy policy(MealReminderContract.Mode mode, boolean kcalEnabled) {
        return new MealReminderPolicy(
                "policy-v1", mode, mode == MealReminderContract.Mode.PILOT, kcalEnabled,
                MealReminderContract.DEFAULT_TIMES, Duration.ofMinutes(60), 3, 3, 1,
                Duration.ofMinutes(180), Duration.ofMinutes(30),
                LocalTime.of(22, 0), LocalTime.of(8, 0));
    }

    private DailyMealReminderSnapshot snapshot(
            Instant evaluatedAt,
            List<MealReminderContract.MealState> mealStates,
            Double remaining,
            boolean calorieReliable,
            boolean targetValid,
            boolean preferences,
            DailyMealReminderSnapshot.ActivityState fasting,
            LocalTime quietStart,
            LocalTime quietEnd
    ) {
        return snapshotForDate(evaluatedAt, DATE, mealStates, remaining, calorieReliable, targetValid,
                preferences, fasting, quietStart, quietEnd);
    }

    private DailyMealReminderSnapshot snapshotForDate(
            Instant evaluatedAt,
            LocalDate date,
            List<MealReminderContract.MealState> mealStates
    ) {
        return snapshotForDate(evaluatedAt, date, mealStates, null, true, true, true,
                DailyMealReminderSnapshot.ActivityState.INACTIVE, null, null);
    }

    private DailyMealReminderSnapshot snapshotForDate(
            Instant evaluatedAt,
            LocalDate date,
            List<MealReminderContract.MealState> mealStates,
            Double remaining,
            boolean calorieReliable,
            boolean targetValid,
            boolean preferences,
            DailyMealReminderSnapshot.ActivityState fasting,
            LocalTime quietStart,
            LocalTime quietEnd
    ) {
        Map<MealReminderContract.Meal, DailyMealReminderSnapshot.MealTotals> totals = totals(mealStates);
        double consumed = totals.values().stream().mapToDouble(DailyMealReminderSnapshot.MealTotals::calories).sum();
        return new DailyMealReminderSnapshot(
                42L, evaluatedAt, date, "Europe/Dublin", 9L, 2L,
                "EFFECTIVE_DATE", "AUTOMATIC", targetValid ? 2000 : 0,
                consumed, 0.0, consumed, remaining, calorieReliable, targetValid,
                totals, states(mealStates), fasting, preferences, preferences, quietStart, quietEnd);
    }

    private static Map<MealReminderContract.Meal, DailyMealReminderSnapshot.MealTotals> totals(
            List<MealReminderContract.MealState> mealStates
    ) {
        Map<MealReminderContract.Meal, DailyMealReminderSnapshot.MealTotals> result = new EnumMap<>(MealReminderContract.Meal.class);
        for (int index = 0; index < MealReminderContract.MAIN_MEALS.size(); index++) {
            boolean recorded = mealStates.get(index) == MealReminderContract.MealState.RECORDED;
            result.put(MealReminderContract.MAIN_MEALS.get(index),
                    new DailyMealReminderSnapshot.MealTotals(recorded ? 1 : 0, 0, recorded ? 300.0 : 0.0, 0.0));
        }
        result.put(MealReminderContract.Meal.SNACK, new DailyMealReminderSnapshot.MealTotals(0, 0, 0, 0));
        return result;
    }

    private static Map<MealReminderContract.Meal, MealReminderContract.MealState> states(
            List<MealReminderContract.MealState> mealStates
    ) {
        Map<MealReminderContract.Meal, MealReminderContract.MealState> result = new EnumMap<>(MealReminderContract.Meal.class);
        for (int index = 0; index < MealReminderContract.MAIN_MEALS.size(); index++) {
            result.put(MealReminderContract.MAIN_MEALS.get(index), mealStates.get(index));
        }
        result.put(MealReminderContract.Meal.SNACK, MealReminderContract.MealState.MISSING);
        return result;
    }

    private static Instant instantFor(MealReminderContract.Slot slot) {
        return switch (slot) {
            case BREAKFAST -> breakfastInstant();
            case LUNCH -> Instant.parse("2026-08-29T13:45:00Z");
            case EVENING -> eveningInstant();
        };
    }

    private static Instant breakfastInstant() {
        return Instant.parse("2026-08-29T09:15:00Z");
    }

    private static Instant eveningInstant() {
        return Instant.parse("2026-08-29T19:45:00Z");
    }

    private static JsonNode loadFixtures() {
        try (InputStream input = MealReminderDecisionEngineTest.class.getResourceAsStream(
                "/meal-reminders/contract-v1.json")) {
            if (input == null) throw new IllegalStateException("Missing meal reminder fixtures");
            return new ObjectMapper().readTree(input);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read meal reminder fixtures", ex);
        }
    }

    private record FixtureInput(
            Instant now,
            DailyMealReminderSnapshot snapshot,
            MealReminderPolicy policy,
            MealReminderRuntimeState runtime
    ) {
    }
}
