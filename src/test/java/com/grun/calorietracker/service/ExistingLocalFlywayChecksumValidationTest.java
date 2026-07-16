package com.grun.calorietracker.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "GRUN_RUN_EXISTING_LOCAL_FLYWAY_VALIDATE", matches = "true")
class ExistingLocalFlywayChecksumValidationTest {

    @Test
    void validatesAppliedMigrationChecksumsWithoutChangingTheDatabase() {
        Flyway flyway = Flyway.configure()
                .dataSource(
                        requiredEnvironmentVariable("SPRING_DATASOURCE_URL"),
                        requiredEnvironmentVariable("SPRING_DATASOURCE_USERNAME"),
                        requiredEnvironmentVariable("SPRING_DATASOURCE_PASSWORD")
                )
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load();

        var validation = flyway.validateWithResult();
        assertTrue(validation.validationSuccessful, () -> validation.invalidMigrations.toString());
        assertNotNull(flyway.info().current());
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for local Flyway validation");
        }
        return value;
    }
}
