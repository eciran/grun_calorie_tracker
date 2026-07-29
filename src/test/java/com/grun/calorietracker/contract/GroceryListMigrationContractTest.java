package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GroceryListMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V197__create_persisted_grocery_lists.sql");

    @Test
    void migrationDefinesOwnershipLifecycleAndOptimisticLocking() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE"));
        assertTrue(sql.contains("source_meal_plan_id BIGINT NOT NULL REFERENCES meal_plans(id) ON DELETE CASCADE"));
        assertTrue(sql.contains("version BIGINT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("uk_grocery_lists_active_meal_plan"));
        assertTrue(sql.contains("WHERE status = 'ACTIVE'"));
    }

    @Test
    void migrationConstrainsGeneratedManualAndEditableItemState() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("source IN ('GENERATED','MANUAL')"));
        assertTrue(sql.contains("quantity_overridden BOOLEAN NOT NULL DEFAULT FALSE"));
        assertTrue(sql.contains("purchased BOOLEAN NOT NULL DEFAULT FALSE"));
        assertTrue(sql.contains("excluded BOOLEAN NOT NULL DEFAULT FALSE"));
        assertTrue(sql.contains("display_quantity > 0 AND display_quantity <= 100000"));
        assertTrue(sql.contains("uk_grocery_list_generated_item"));
    }
}
