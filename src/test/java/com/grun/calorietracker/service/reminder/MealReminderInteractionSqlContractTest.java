package com.grun.calorietracker.service.reminder;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MealReminderInteractionSqlContractTest {
    @Test
    void migrationKeepsOpenAndMealLogInteractionsIdempotentAndAccountBound() throws Exception {
        String sql = new String(getClass().getResourceAsStream(
                "/db/migration/V234__add_meal_reminder_interactions.sql").readAllBytes(),
                StandardCharsets.UTF_8).toLowerCase();
        assertTrue(sql.contains("user_id bigint not null references users(id) on delete cascade"));
        assertTrue(sql.contains("occurrence_id bigint references meal_reminder_occurrences(id)"));
        assertTrue(sql.contains("notification_id bigint references notifications(id)"));
        assertTrue(sql.contains("unique (event_key)"));
        assertTrue(sql.contains("'open','meal_log_conversion','opt_out'"));
    }
}
