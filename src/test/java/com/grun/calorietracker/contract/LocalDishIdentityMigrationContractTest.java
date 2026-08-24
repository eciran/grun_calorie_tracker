package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDishIdentityMigrationContractTest {

    @Test
    void migrationAddsNullableFamilyVariantIdentityAndFocusedIndex() throws IOException {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V208__add_local_dish_identity.sql")
        ).toLowerCase(Locale.ROOT);

        assertTrue(migration.contains("add column if not exists dish_family_key varchar(160)"));
        assertTrue(migration.contains("add column if not exists dish_variant_key varchar(160)"));
        assertTrue(migration.contains("idx_food_items_local_dish_family_variant"));
        assertTrue(migration.contains("where catalog_type = 'local_dish'"));
    }
}
