package com.grun.calorietracker.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "GRUN_RUN_CORE_SEARCH_POSTGRES", matches = "true")
class BroadSourceFoodSearchPostgresIntegrationTest extends BroadSourceFoodSearchIntegrationTest {
    @DynamicPropertySource static void ownedDatabase(DynamicPropertyRegistry registry) {
        String url = System.getenv("GRUN_R7_RETAILER_TEST_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/grun_r7_retailer_test"))
            throw new IllegalStateException("Only the disposable localhost broad-source DB is allowed");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "r7_test");
        registry.add("spring.datasource.password", () -> "");
    }
}
