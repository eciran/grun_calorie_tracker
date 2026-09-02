package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationReleaseConfigurationContractTest {

    @Test
    void everyRuntimeConfigurationDefaultsReleaseAndProducersToOff() throws Exception {
        for (String file : new String[]{"application.yml", "application-example.yml", "application-prod.yml"}) {
            String yaml = Files.readString(Path.of("src/main/resources", file));
            assertTrue(yaml.contains("stage: ${GRUN_NOTIFICATION_RELEASE_STAGE:OFF}"), file);
            assertTrue(yaml.contains("live-percentage: ${GRUN_NOTIFICATION_LIVE_PERCENTAGE:0}"), file);
            assertTrue(yaml.contains("water-enabled: ${GRUN_NOTIFICATION_MIGRATE_WATER:false}"), file);
            assertTrue(yaml.contains("step-enabled: ${GRUN_NOTIFICATION_MIGRATE_STEP:false}"), file);
            assertTrue(yaml.contains("basic-fasting-enabled: ${GRUN_NOTIFICATION_MIGRATE_BASIC_FASTING:false}"), file);
        }
    }
}
