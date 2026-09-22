package com.grun.calorietracker.contract;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "GRUN_DEPLOY_VALIDATION_JDBC_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:55439/grun_deploy_validation")
class DeploymentPostgresMigrationTest {
    @Test
    void migratesDisposablePostgresAndValidatesChecksums() {
        Flyway flyway = Flyway.configure()
                .dataSource(System.getenv("GRUN_DEPLOY_VALIDATION_JDBC_URL"),
                        "postgres", System.getenv("GRUN_DEPLOY_VALIDATION_DB_PASSWORD"))
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load();
        flyway.migrate();
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals("280", flyway.info().current().getVersion().getVersion());
    }

    @Test
    void ownerOperationsSchemaIsCompleteThroughV273() {
        var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                System.getenv("GRUN_DEPLOY_VALIDATION_JDBC_URL"), "postgres",
                System.getenv("GRUN_DEPLOY_VALIDATION_DB_PASSWORD"));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .cleanDisabled(true).load().migrate();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);

        for (String table : java.util.List.of(
                "owner_error_events", "owner_operational_alerts",
                "owner_error_group_states", "owner_error_group_state_history")) {
            assertEquals(1, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_name = ?
                    """, Integer.class, table), table + " must exist");
        }
        for (String column : java.util.List.of("source", "client_platform", "app_version")) {
            assertEquals(1, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'owner_error_events' AND column_name = ?
                    """, Integer.class, column), "owner_error_events." + column + " must exist");
        }

        String actionConstraint = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conname = 'chk_admin_action_audits_action_type'
                  AND conrelid = 'admin_action_audits'::regclass
                """, String.class);
        String targetConstraint = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conname = 'chk_admin_action_audits_target_type'
                  AND conrelid = 'admin_action_audits'::regclass
                """, String.class);
        assertNotNull(actionConstraint);
        assertNotNull(targetConstraint);
        assertTrue(actionConstraint.contains("OWNER_ALERT_RETRY"));
        assertTrue(actionConstraint.contains("OWNER_ALERT_ACKNOWLEDGE"));
        assertTrue(actionConstraint.contains("OWNER_ERROR_GROUP_STATUS_UPDATE"));
        assertTrue(targetConstraint.contains("OWNER_OPERATIONAL_ALERT"));
        assertTrue(targetConstraint.contains("OWNER_ERROR_GROUP"));
    }

    @Test
    void competingAccountsCannotBothClaimOneStoreSubscription() throws Exception {
        var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                System.getenv("GRUN_DEPLOY_VALIDATION_JDBC_URL"), "postgres",
                System.getenv("GRUN_DEPLOY_VALIDATION_DB_PASSWORD"));
        var flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .cleanDisabled(true).load();
        flyway.migrate();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        var service = new com.grun.calorietracker.service.StoreSubscriptionOwnershipService(jdbc);
        var transactions = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
        transactions.setTimeout(15);
        var chain = "concurrency-test-" + java.util.UUID.randomUUID();
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (long id = 900001; id <= 900002; id++) {
                final long userId = id;
                futures.add(executor.submit(() -> {
                    start.await();
                    var event = new com.grun.calorietracker.dto.RevenueCatWebhookEventDto.Event();
                    event.setId(chain + userId); event.setStore("APP_STORE");
                    event.setEnvironment("SANDBOX"); event.setOriginalTransactionId(chain);
                    try {
                        transactions.executeWithoutResult(status -> service.assertOwner(userId, event));
                        return true;
                    } catch (IllegalArgumentException conflict) {
                        assertTrue(conflict.getMessage().contains("SUBSCRIPTION_OWNERSHIP_CONFLICT"));
                        return false;
                    }
                }));
            }
            start.countDown();
            int winners = 0;
            for (var future : futures) if (future.get(20, java.util.concurrent.TimeUnit.SECONDS)) winners++;
            assertEquals(1, winners);
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM store_subscription_ownership WHERE original_transaction_id = ?",
                    Integer.class, chain));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test
    void historicalOwnershipBackfillSeparatesEnvironmentsAndQuarantinesConflicts() {
        var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                System.getenv("GRUN_DEPLOY_VALIDATION_JDBC_URL"), "postgres",
                System.getenv("GRUN_DEPLOY_VALIDATION_DB_PASSWORD"));
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        var transactions = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
        transactions.executeWithoutResult(status -> {
            String schema = "ownership_fixture_" + java.util.UUID.randomUUID().toString().replace("-", "");
            jdbc.execute("CREATE SCHEMA " + schema);
            jdbc.execute("SET LOCAL search_path TO " + schema);
            jdbc.execute("""
                    CREATE TABLE subscription_provider_events (
                        user_id BIGINT, provider_event_id TEXT, provider TEXT, raw_payload TEXT)
                    """);
            for (int userId = 1; userId <= 2; userId++) {
                jdbc.update("INSERT INTO subscription_provider_events VALUES (?, ?, 'REVENUECAT', ?)",
                        userId, "sandbox-" + userId,
                        """
                        {"event":{"store":"APP_STORE","environment":"SANDBOX","original_transaction_id":"chain"}}
                        """);
            }
            jdbc.update("INSERT INTO subscription_provider_events VALUES (3, 'production', 'REVENUECAT', ?)",
                    """
                    {"event":{"store":"APP_STORE","environment":"PRODUCTION","original_transaction_id":"chain"}}
                    """);
            new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                    new org.springframework.core.io.ClassPathResource("db/migration/V262__store_subscription_ownership.sql"))
                    .execute(dataSource);
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM store_subscription_ownership", Integer.class));
            assertEquals(true, jdbc.queryForObject(
                    "SELECT requires_review FROM store_subscription_ownership WHERE environment = 'SANDBOX'", Boolean.class));
            assertEquals(false, jdbc.queryForObject(
                    "SELECT requires_review FROM store_subscription_ownership WHERE environment = 'PRODUCTION'", Boolean.class));
            assertEquals(3L, jdbc.queryForObject(
                    "SELECT owner_user_id FROM store_subscription_ownership WHERE environment = 'PRODUCTION'", Long.class));
            var event = new com.grun.calorietracker.dto.RevenueCatWebhookEventDto.Event();
            event.setStore("APP_STORE"); event.setEnvironment("SANDBOX");
            event.setOriginalTransactionId("chain"); event.setId("retry");
            var service = new com.grun.calorietracker.service.StoreSubscriptionOwnershipService(jdbc);
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> service.assertOwner(1L, event));
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> service.assertOwner(2L, event));
            status.setRollbackOnly();
        });
    }
}
