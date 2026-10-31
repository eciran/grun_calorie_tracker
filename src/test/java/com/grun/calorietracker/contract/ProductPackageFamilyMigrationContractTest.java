package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductPackageFamilyMigrationContractTest {
    @Test
    void migrationPreservesBarcodeProductsAndRequiresPositivePackageMetadata() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V315__add_product_package_families.sql"
        ));
        assertTrue(sql.contains("food_product_families"));
        assertTrue(sql.contains("food_product_family_members"));
        assertTrue(sql.contains("UNIQUE (food_item_id)"));
        assertTrue(sql.contains("package_quantity > 0"));
        assertTrue(sql.contains("REFERENCES food_items(id)"));
        assertTrue(sql.contains("grun_product_cache_write_fence"));

        String reversibleSql = Files.readString(Path.of(
                "src/main/resources/db/migration/V316__allow_reversible_product_family_memberships.sql"
        ));
        assertTrue(reversibleSql.contains("ADD COLUMN active"));
        assertTrue(reversibleSql.contains("DROP CONSTRAINT uq_food_product_family_member_product"));
        assertTrue(reversibleSql.toLowerCase().contains("where active = true"));

        String reapplySql = Files.readString(Path.of(
                "src/main/resources/db/migration/V317__allow_reapplying_reverted_product_families.sql"
        ));
        assertTrue(reapplySql.contains("DROP CONSTRAINT uq_food_product_family_key"));
        assertTrue(reapplySql.contains("uq_food_product_family_active_key"));
        assertTrue(reapplySql.toLowerCase().contains("where active = true"));

        String packageMetadataSql = Files.readString(Path.of(
                "src/main/resources/db/migration/V318__persist_food_item_package_metadata.sql"
        ));
        assertTrue(packageMetadataSql.contains("ADD COLUMN package_quantity NUMERIC(12,3)"));
        assertTrue(packageMetadataSql.contains("ADD COLUMN package_unit VARCHAR(24)"));
        assertTrue(packageMetadataSql.contains("package_quantity > 0"));
        assertTrue(packageMetadataSql.contains("'GRAM', 'MILLILITER'"));
        assertTrue(packageMetadataSql.contains("idx_food_item_package_metadata"));
        assertTrue(packageMetadataSql.toLowerCase().contains("where package_quantity is not null"));
    }
}
