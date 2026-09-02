package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BehaviorReminderMigrationContractTest {

    @Test
    void waterStepAndBasicFastingUseSharedOrchestratorBoundary() throws IOException {
        for (String service : List.of(
                "WaterTrackingServiceImpl.java",
                "StepTrackingServiceImpl.java",
                "FastingTrackingServiceImpl.java"
        )) {
            String source = Files.readString(Path.of(
                    "src/main/java/com/grun/calorietracker/service/impl", service));
            assertTrue(source.contains("BehaviorReminderNotificationService"), service);
            assertFalse(source.contains("PushDeliveryService"), service);
            assertFalse(source.contains("NotificationRepository"), service);
        }
    }

    @Test
    void existingPlatformMigrationClassifiesAllThreeDefinitionsAsBehaviorReminders() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V240__add_notification_orchestration_platform.sql"));
        assertTrue(migration.contains("'fasting_reminder', 'step_reminder', 'water_reminder'"));
        assertTrue(migration.contains("SET classification = 'BEHAVIOR_REMINDER'"));
    }

    @Test
    void producerRolloutFlagsAreExplicitlyOffInDefaultExampleAndProductionConfiguration() throws IOException {
        for (String config : List.of("application.yml", "application-example.yml", "application-prod.yml")) {
            String yaml = Files.readString(Path.of("src/main/resources", config));
            assertTrue(yaml.contains("GRUN_NOTIFICATION_MIGRATE_WATER:false"), config);
            assertTrue(yaml.contains("GRUN_NOTIFICATION_MIGRATE_STEP:false"), config);
            assertTrue(yaml.contains("GRUN_NOTIFICATION_MIGRATE_BASIC_FASTING:false"), config);
        }
    }
}
