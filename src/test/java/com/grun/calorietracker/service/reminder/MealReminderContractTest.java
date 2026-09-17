package com.grun.calorietracker.service.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.grun.calorietracker.service.reminder.MealReminderContract.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MR-01 tests the vocabulary, evidence classification, seed copy and fixture consistency.
 * Decision fixtures must also be run against the real MR-03 engine when it exists.
 * These tests intentionally do not claim to exercise a scheduler, database or push provider.
 */
class MealReminderContractTest {
    private static final JsonNode FIXTURES = loadFixtures();

    @Test
    void contractHasSafeDefaultsAndNoImplicitSnackSlot() {
        assertEquals(VERSION, FIXTURES.path("version").asText());
        assertEquals(Mode.OFF, DEFAULT_MODE);
        assertFalse(DELIVERY_ENABLED_BY_DEFAULT);
        assertEquals("IN_APP_AND_PUSH", DEFAULT_CHANNEL);
        assertEquals("NOTIFICATIONS", RETENTION_POLICY_KEY);
        assertEquals(3, MAX_DAILY_OCCURRENCES);
        assertEquals(3, MAX_ROLLING_24H_OCCURRENCES);
        assertEquals(1, MAX_DAILY_CATCHUPS);
        assertEquals(Duration.ofMinutes(180), MIN_MEAL_REMINDER_GAP);
        assertEquals(Duration.ofMinutes(30), ROUTINE_REMINDER_GAP);
        assertEquals(Duration.ofMinutes(60), MAX_SLOT_AGE);
        assertEquals(Duration.ofMinutes(5), SCAN_INTERVAL);
        assertEquals(LocalTime.of(22, 0), DEFAULT_QUIET_START);
        assertEquals(LocalTime.of(8, 0), DEFAULT_QUIET_END);
        assertEquals(List.of(Meal.BREAKFAST, Meal.LUNCH, Meal.DINNER), MAIN_MEALS);
        assertEquals(Set.copyOf(MAIN_MEALS), DEFAULT_TIMES.keySet());
        assertEquals(LocalTime.of(10, 0), DEFAULT_TIMES.get(Meal.BREAKFAST));
        assertEquals(LocalTime.of(14, 30), DEFAULT_TIMES.get(Meal.LUNCH));
        assertEquals(LocalTime.of(20, 30), DEFAULT_TIMES.get(Meal.DINNER));
        assertThrows(UnsupportedOperationException.class, () -> MAIN_MEALS.add(Meal.SNACK));
        assertThrows(UnsupportedOperationException.class,
                () -> DEFAULT_TIMES.put(Meal.BREAKFAST, LocalTime.NOON));
    }

    @TestFactory
    Stream<DynamicTest> evidenceFixturesExerciseCanonicalMealClassification() {
        return rows("mealEvidenceCases").map(row -> DynamicTest.dynamicTest(row.path("id").asText(), () -> {
            assertFalse(row.path("context").asText().isBlank());
            MealEvidence evidence = new MealEvidence(requiredBoolean(row, "reliable"),
                    requiredInt(row, "food"), requiredInt(row, "recipe"),
                    requiredBoolean(row, "wholeMealExcluded"));
            assertEquals(MealState.valueOf(row.path("expected").asText()), classify(evidence));
        }));
    }

    @Test
    void invalidEvidenceCannotBeSilentlyTreatedAsMissing() {
        assertThrows(NullPointerException.class, () -> classify(null));
        assertThrows(IllegalArgumentException.class, () -> new MealEvidence(true, -1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new MealEvidence(true, 0, -1, false));
        assertEquals(MealState.UNKNOWN, classify(new MealEvidence(false, 1, 1, true)));
    }

    @Test
    void prerequisitesAndEveningOccurrenceAreStable() {
        assertEquals(List.of(), precedingMeals(Meal.BREAKFAST));
        assertEquals(List.of(Meal.BREAKFAST), precedingMeals(Meal.LUNCH));
        assertEquals(List.of(Meal.BREAKFAST, Meal.LUNCH), precedingMeals(Meal.DINNER));
        assertThrows(IllegalArgumentException.class, () -> precedingMeals(Meal.SNACK));
        assertEquals(Message.DINNER.slot(), Message.DAILY_CATCHUP.slot());
        assertEquals(Message.DINNER.slot(), Message.DINNER_KCAL.slot());
        assertEquals(Slot.EVENING, Message.DINNER.slot());
        assertEquals(5, Arrays.stream(Message.values()).map(Message::definitionKey).distinct().count());
    }

    @TestFactory
    Stream<DynamicTest> decisionFixtureExpectationsObeyTheAgreedInvariants() {
        return rows("decisionCases").map(row -> DynamicTest.dynamicTest(row.path("id").asText(), () -> {
            List<MealState> states = StreamSupport.stream(row.path("states").spliterator(), false)
                    .map(value -> MealState.valueOf(value.asText())).toList();
            assertEquals(3, states.size());
            Slot slot = Slot.valueOf(row.path("slot").asText());
            Reason reason = Reason.valueOf(row.path("reason").asText());
            assertTrue(row.has("message"));
            assertTrue(row.has("kcalReason"));
            assertTrue(row.path("conditions").isArray());
            List<Reason> blockers = StreamSupport.stream(row.path("conditions").spliterator(), false)
                    .map(value -> Reason.valueOf(value.asText()))
                    .filter(value -> value != Reason.QUIET_HOURS).toList();
            assertTrue(SUPPRESSION_PRIORITY.containsAll(blockers));
            if (!blockers.isEmpty()) {
                assertTrue(row.path("message").isNull());
                assertEquals(SUPPRESSION_PRIORITY.stream().filter(blockers::contains).findFirst().orElseThrow(), reason);
            }
            if (states.contains(MealState.UNKNOWN)) {
                assertTrue(row.path("message").isNull(), "Unknown data must not produce a message");
            }
            if (row.path("message").isNull()) {
                assertTrue(row.path("kcalReason").isNull());
                assertNotEquals(Reason.ELIGIBLE, reason);
                if (reason == Reason.DAY_COMPLETE) {
                    assertFalse(states.contains(MealState.MISSING));
                    assertFalse(states.contains(MealState.UNKNOWN));
                }
                if (reason == Reason.CATCHUP_LIMIT) {
                    assertTrue(row.path("catchupsSent").asInt() >= MAX_DAILY_CATCHUPS);
                }
                return;
            }
            Message message = Message.valueOf(row.path("message").asText());
            KcalReason kcalReason = KcalReason.valueOf(row.path("kcalReason").asText());
            assertEquals(slot, message.slot());
            assertTrue(states.contains(MealState.MISSING));
            switch (message) {
                case BREAKFAST -> assertEquals(MealState.MISSING, states.get(0));
                case LUNCH -> {
                    assertEquals(MealState.MISSING, states.get(1));
                }
                case DINNER, DINNER_KCAL -> {
                    assertNotEquals(MealState.MISSING, states.get(0));
                    assertNotEquals(MealState.MISSING, states.get(1));
                    assertEquals(MealState.MISSING, states.get(2));
                }
                case DAILY_CATCHUP -> {
                    assertTrue(states.get(0) == MealState.MISSING || states.get(1) == MealState.MISSING);
                    assertNotEquals(KcalReason.SHOWN, kcalReason);
                    assertTrue(row.path("catchupsSent").asInt() < MAX_DAILY_CATCHUPS);
                }
            }
            if (message == Message.DINNER_KCAL) {
                assertEquals(KcalReason.SHOWN, kcalReason);
                BigDecimal remainder = new BigDecimal(row.path("remainingKcal").asText());
                assertDoesNotThrow(() -> renderInitialCopy(message, "tr", remainder));
                assertTrue(states.contains(MealState.RECORDED));
            } else {
                assertNotEquals(KcalReason.SHOWN, kcalReason);
            }
        }));
    }

    @TestFactory
    Stream<DynamicTest> defaultLocalWindowsHaveInclusiveStartAndExclusiveEnd() {
        return rows("localSlotCases").map(row -> DynamicTest.dynamicTest(row.path("id").asText(), () -> {
            LocalTime start = DEFAULT_TIMES.get(Meal.valueOf(row.path("slot").asText()));
            LocalTime time = LocalTime.parse(row.path("time").asText());
            boolean within = !time.isBefore(start) && time.isBefore(start.plus(MAX_SLOT_AGE));
            assertEquals(requiredBoolean(row, "within"), within);
        }));
    }

    @TestFactory
    Stream<DynamicTest> seedCopyIsLocalizedNeutralAndUsesOnlyTypedKcalParameter() {
        return Arrays.stream(Message.values()).flatMap(message -> Stream.of("tr", "en")
                .map(language -> DynamicTest.dynamicTest(message + "-" + language, () -> {
                    Copy copy = initialCopy(message, language);
                    assertFalse(copy.title().isBlank());
                    assertTrue(copy.title().length() <= 80);
                    assertFalse(copy.body().isBlank());
                    assertTrue(copy.body().length() <= 240);
                    Set<String> placeholders = new HashSet<>();
                    var matcher = java.util.regex.Pattern.compile("\\{([^{}]+)}").matcher(copy.body());
                    while (matcher.find()) placeholders.add(matcher.group(1));
                    assertEquals(message.allowedParameters(), placeholders);
                    String normalized = copy.body().toLowerCase(Locale.ROOT);
                    for (String prohibited : List.of("yemelisin", "başarısız", "kalorini yak", "hedefini aştın", "must eat", "you failed", "burn off")) {
                        assertFalse(normalized.contains(prohibited), prohibited);
                    }
                    Copy rendered = renderInitialCopy(message, language, new BigDecimal("650.50"));
                    assertFalse(rendered.body().contains("{"));
                    if (message == Message.DINNER_KCAL) {
                        assertTrue(rendered.body().contains("651 kcal"));
                    } else {
                        assertFalse(rendered.body().contains("kcal"));
                        assertEquals(copy, rendered);
                    }
                })));
    }

    @Test
    void languageFallbackAndNumericRenderingAreDeterministic() {
        assertEquals(initialCopy(Message.LUNCH, "tr"), initialCopy(Message.LUNCH, "tr-TR"));
        assertEquals(initialCopy(Message.LUNCH, "tr"), initialCopy(Message.LUNCH, " TR_tr "));
        assertEquals(initialCopy(Message.LUNCH, "en"), initialCopy(Message.LUNCH, null));
        assertEquals(initialCopy(Message.LUNCH, "en"), initialCopy(Message.LUNCH, "de"));
        assertThrows(IllegalArgumentException.class, () -> renderInitialCopy(Message.DINNER_KCAL, "en", null));
        for (String invalid : List.of("-1", "0", "0.49")) {
            assertThrows(IllegalArgumentException.class,
                    () -> renderInitialCopy(Message.DINNER_KCAL, "tr", new BigDecimal(invalid)));
        }
        assertTrue(renderInitialCopy(Message.DINNER_KCAL, "tr", new BigDecimal("0.50")).body().contains("1 kcal"));
        assertDoesNotThrow(() -> renderInitialCopy(Message.DINNER, "tr", null));
    }

    @Test
    void defaultSlotsUseLocalCivilTimeAcrossDstChanges() {
        ZoneId london = ZoneId.of("Europe/London");
        LocalTime breakfast = DEFAULT_TIMES.get(Meal.BREAKFAST);
        var beforeSpring = LocalDate.of(2026, 3, 28).atTime(breakfast).atZone(london);
        var afterSpring = LocalDate.of(2026, 3, 29).atTime(breakfast).atZone(london);
        assertEquals(breakfast, afterSpring.toLocalTime());
        assertEquals(Duration.ofHours(23), Duration.between(beforeSpring.toInstant(), afterSpring.toInstant()));
        var beforeAutumn = LocalDate.of(2026, 10, 24).atTime(breakfast).atZone(london);
        var afterAutumn = LocalDate.of(2026, 10, 25).atTime(breakfast).atZone(london);
        assertEquals(breakfast, afterAutumn.toLocalTime());
        assertEquals(Duration.ofHours(25), Duration.between(beforeAutumn.toInstant(), afterAutumn.toInstant()));
    }

    @Test
    void fixtureIdsAreUniqueAndCoverAllPublicStatesAndMessages() {
        for (String group : List.of("mealEvidenceCases", "decisionCases", "localSlotCases")) {
            List<String> ids = rows(group).map(row -> row.path("id").asText()).toList();
            assertFalse(ids.isEmpty());
            assertFalse(ids.contains(""));
            assertEquals(ids.size(), new HashSet<>(ids).size(), group);
        }
        assertEquals(Set.of(MealState.values()), rows("mealEvidenceCases")
                .map(row -> MealState.valueOf(row.path("expected").asText())).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(Message.values()), rows("decisionCases").filter(row -> !row.path("message").isNull())
                .map(row -> Message.valueOf(row.path("message").asText())).collect(java.util.stream.Collectors.toSet()));
    }

    private static boolean requiredBoolean(JsonNode node, String field) {
        assertTrue(node.path(field).isBoolean(), field);
        return node.path(field).asBoolean();
    }

    private static int requiredInt(JsonNode node, String field) {
        assertTrue(node.path(field).isInt(), field);
        return node.path(field).asInt();
    }

    private static Stream<JsonNode> rows(String group) {
        assertTrue(FIXTURES.path(group).isArray(), group);
        return StreamSupport.stream(FIXTURES.path(group).spliterator(), false);
    }

    private static JsonNode loadFixtures() {
        try (InputStream stream = MealReminderContractTest.class.getResourceAsStream("/meal-reminders/contract-v1.json")) {
            if (stream == null) throw new IllegalStateException("Missing reminder contract fixtures");
            return new ObjectMapper().readTree(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Invalid reminder contract fixtures", exception);
        }
    }
}
