package com.grun.calorietracker.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.clean-disabled=true"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "GRUN_RUN_PRODUCT_FLYWAY_POSTGRES", matches = "true")
class FoodProductFlywayPostgresIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void appliesCompleteMigrationChainWithoutPendingMigrations() {
        var info = flyway.info();

        assertNotNull(info.current());
        assertEquals(0, info.pending().length);
    }
}