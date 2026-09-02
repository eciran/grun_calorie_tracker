package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.config.MealReminderDeliveryProperties;
import com.grun.calorietracker.config.PushProperties;
import com.grun.calorietracker.enums.PushProvider;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MealReminderReleaseReadinessContractTest {
    private static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");

    @Test
    void deliveryAndSharedPushRemainOffByDefault() {
        MealReminderDeliveryProperties delivery = new MealReminderDeliveryProperties();
        PushProperties push = new PushProperties();

        assertFalse(delivery.isDeliveryEnabled());
        assertEquals(MealReminderContract.Mode.OFF, delivery.getMode());
        assertEquals(100, delivery.getCandidateBatchSize());
        assertEquals(100, delivery.getOutboxBatchSize());
        assertFalse(push.isEnabled());
        assertEquals(PushProvider.LOG, push.getProvider());
    }

    @Test
    void reminderMigrationSeriesIsUniqueAndContiguousWithinItsOwnedRange() throws IOException {
        Pattern versionPattern = Pattern.compile("^V(\\d+)__.+\\.sql$");
        List<Integer> versions;
        try (var files = Files.list(MIGRATIONS)) {
            versions = files.map(path -> path.getFileName().toString())
                    .map(versionPattern::matcher).filter(Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1))).sorted().toList();
        }

        assertEquals(versions.size(), versions.stream().distinct().count(), "duplicate Flyway version");
        assertTrue(versions.containsAll(List.of(230, 231, 232, 233, 234)));
        assertEquals(List.of(230, 231, 232, 233, 234),
                versions.stream().filter(version -> version >= 230 && version <= 234).toList());
        assertTrue(versions.get(versions.size() - 1) >= 240,
                "later product migrations may follow the completed meal-reminder range");
    }

    @Test
    void releaseEvidenceNamesEveryScenarioAndKeepsExternalGatesWaiting() throws IOException {
        String matrix = Files.readString(Path.of("docs", "MEAL_REMINDER_ACCEPTANCE_MATRIX_2026-08-29.md"));
        String runbook = Files.readString(Path.of("docs", "MEAL_REMINDER_RELEASE_RUNBOOK_2026-08-29.md"));

        for (int number = 1; number <= 28; number++) {
            assertTrue(matrix.contains(String.format("A%02d", number)), "missing A" + number);
        }
        for (String required : List.of("OFF / NOT READY", "UNASSIGNED — WAITING", "5%", "25%", "100%",
                "two complete local days", "wrong kcal", "preference violation", "cannot be retracted",
                "Actual Flyway validation", "physical devices")) {
            assertTrue(runbook.contains(required), required);
        }
    }
}
