package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationDefinitionMigrationContractTest {
    @Test
    void migrationCreatesManagedDefinitionsForCurrentAutomaticNotificationTypes() throws Exception {
        String resource = "db/migration/V222__add_managed_notification_definitions.sql";
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource + " missing");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("CREATE TABLE notification_definitions"));
            for (String key : List.of(
                    "ai_request_ready", "ai_request_failed", "ai_quota_refund_approved",
                    "ai_quota_refund_rejected", "recipe_review_approved", "recipe_review_rejected",
                    "product_intake", "fasting_reminder", "step_reminder", "water_reminder",
                    "subscription", "ai_rejection_alert", "system_alert",
                    "subscription_provider_alert", "admin_security_alert")) {
                assertTrue(sql.contains("('" + key + "'"), "Missing notification definition seed: " + key);
            }
        }
    }
}
