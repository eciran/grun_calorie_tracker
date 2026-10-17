package com.grun.calorietracker.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false",
        "spring.flyway.enabled=true",
        "spring.flyway.clean-disabled=true",
        "spring.flyway.baseline-on-migrate=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "GRUN_RUN_CATALOG_AUDIT_POSTGRES", matches = "true")
class ProductCatalogAuditPostgresIntegrationTest extends ProductCatalogAuditIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String url = System.getenv("GRUN_CATALOG_AUDIT_TEST_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/grun_r2_audit_test")) {
            throw new IllegalStateException("Only the explicitly named disposable localhost audit DB is allowed");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "audit_test");
        registry.add("spring.datasource.password", () -> System.getenv("GRUN_CATALOG_AUDIT_TEST_PASSWORD"));
    }

    @Test
    void reviewedIdentityPairIsConstrainedAndRetainsSourceAndDatabaseId() {
        var product = food("Stable Banana");
        long id = product.getId();
        rejectsCheckConstraint("update food_items set food_concept_key = 'banana' where id = " + id);
        jdbc.update("update food_items set food_concept_key = ?, composition_variant_key = ? where id = ?",
                "banana", "edible_flesh", id);
        var stored = foods.findById(id).orElseThrow();
        assertEquals(id, stored.getId());
        assertEquals(product.getSourceKey(), stored.getSourceKey());
        String identity = com.grun.calorietracker.service.support.FoodProductCanonicalKeyRules.resolve(stored);
        stored.setDisplayName("Muz");
        stored.setMarketRegion(com.grun.calorietracker.enums.MarketRegion.TR);
        assertEquals(identity, com.grun.calorietracker.service.support.FoodProductCanonicalKeyRules.resolve(stored));
    }

    @Test
    void validatesFullMigrationChainAndDatabaseConstraints() {
        assertEquals("311", flyway.info().current().getVersion().getVersion());
        assertEquals(0, flyway.info().pending().length);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(1L, jdbc.queryForObject("select count(*) from product_catalog_audit_state", Long.class));
        assertNotNull(jdbc.queryForObject("select indexname from pg_indexes where indexname = 'idx_product_catalog_audit_retry'", String.class));
        assertNotNull(jdbc.queryForObject("select indexname from pg_indexes where indexname = 'idx_food_items_concept_variant'", String.class));
        rejectsCheckConstraint("update product_catalog_audit_state set cursor_id = 1, high_water_id = 0 where id = 1");
        rejectsCheckConstraint("update product_catalog_audit_state set lease_owner = 'invalid', lease_until = null where id = 1");
        rejectsCheckConstraint("insert into product_catalog_audit_state(id) values (2)");
    }

    private void rejectsCheckConstraint(String sql) {
        var failure = assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(sql));
        var cause = assertInstanceOf(java.sql.SQLException.class, failure.getMostSpecificCause());
        assertEquals("23514", cause.getSQLState());
    }

    @Test
    void concurrentWorkersUseOneLeaseAndScanEachProductOnlyOnce() throws Exception {
        food("Concurrent one");
        food("Concurrent two");
        service.start("admin@test");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(suggestions.createRuleSuggestionsForAudit(any())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timed out");
            return 0;
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(service::step);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var secondEntered = new CountDownLatch(1);
            var second = executor.submit(() -> {
                secondEntered.countDown();
                return newWorker().step();
            });
            assertTrue(secondEntered.await(5, TimeUnit.SECONDS));
            Thread.sleep(200);
            assertFalse(second.isDone(), "Second worker must wait for the locked checkpoint transaction");
            release.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            assertEquals("COMPLETED", service.status().status());
            assertEquals(2, items.count());
            assertEquals(2, runs.findById(service.status().scanRunId()).orElseThrow().getScannedProducts());
            assertTrue(items.findAll().stream().allMatch(item -> item.getAttempts() == 1));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
