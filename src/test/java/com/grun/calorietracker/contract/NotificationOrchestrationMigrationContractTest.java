package com.grun.calorietracker.contract;

import com.grun.calorietracker.config.NotificationDeliveryProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class NotificationOrchestrationMigrationContractTest {
    @Test
    void sharedDeliveryRemainsOffByDefault() {
        NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
        assertFalse(properties.isEnabled());
        assertEquals(100, properties.getOutboxBatchSize());
        assertEquals(3, properties.getMaxAttempts());
    }

    @Test
    void migrationProvidesTypedDefinitionsAndIdempotentLeasedOutbox() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V240__add_notification_orchestration_platform.sql"))
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        assertTrue(sql.contains("create table notification_occurrences"));
        assertTrue(sql.contains("unique (source, source_event_id, definition_key, user_id)"));
        assertTrue(sql.contains("create table notification_outbox"));
        assertTrue(sql.contains("unique (occurrence_id, channel)"));
        assertTrue(sql.contains("create table notification_delivery_attempts"));
        assertTrue(sql.contains("unique (outbox_id, push_token_id)"));
        assertTrue(sql.contains("subscription_started"));
        assertTrue(sql.contains("subscription_billing_issue"));
        assertTrue(sql.contains("subscription_expired"));
        assertTrue(sql.contains("protected_definition"));
        assertTrue(sql.contains("transactional_account"));
    }
}
