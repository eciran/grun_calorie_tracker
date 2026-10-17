package com.grun.calorietracker.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.show_sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "GRUN_RUN_CORE_SEARCH_POSTGRES", matches = "true")
class MixedSourceFoodSearchPostgresIntegrationTest extends MixedSourceFoodSearchIntegrationTest {
    @DynamicPropertySource static void ownedDatabase(DynamicPropertyRegistry registry) {
        String url = System.getenv("GRUN_R7_MIXED_TEST_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/grun_r7_mixed_test")) {
            throw new IllegalStateException("Only the owned disposable localhost mixed-source DB is permitted");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "r7_test");
        registry.add("spring.datasource.password", () -> "");
    }
}
