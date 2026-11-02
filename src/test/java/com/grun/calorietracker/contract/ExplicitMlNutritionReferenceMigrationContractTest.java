package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplicitMlNutritionReferenceMigrationContractTest {

    @Test
    void repairsOnlyExplicitMlLegacyRowsWithoutChangingIdentityOrNutrition() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V320__repair_explicit_ml_nutrition_reference.sql"
        )).toLowerCase();

        assertTrue(sql.contains("nutrition_reference_unit = 'per_100ml'"));
        assertTrue(sql.contains("nutrition_reference_unit = 'per_100g'"));
        assertTrue(sql.contains("lower(trim(serving_unit))"));
        assertTrue(sql.contains("serving_size_grams = null"));
        assertFalse(sql.contains("display_name ="));
        assertFalse(sql.contains("name ="));
        assertFalse(sql.contains("barcode ="));
        assertFalse(sql.contains("source_key ="));
        assertFalse(sql.contains("calories ="));
    }

    @Test
    void verifiedVolumeFamiliesAreRepairedInASeparateForwardMigration() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V321__repair_verified_volume_product_families.sql"
        )).toLowerCase();

        assertTrue(sql.contains("food_product_family_members"));
        assertTrue(sql.contains("sibling.package_unit <> 'milliliter'"));
        assertTrue(sql.contains("bridge.quality_status = 'verified'"));
        assertTrue(sql.contains("bridge.gram_weight > 0"));
        assertTrue(sql.contains("bridge.ml_volume > 0"));
        assertFalse(sql.contains("display_name ="));
        assertFalse(sql.contains("barcode ="));
        assertFalse(sql.contains("source_key ="));
        assertFalse(sql.contains("calories ="));
    }

    @Test
    void allVolumeFamiliesDoNotRequireAnInventedDensityBridge() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V322__repair_all_volume_product_families.sql"
        )).toLowerCase();

        assertTrue(sql.contains("member.package_unit = 'milliliter'"));
        assertTrue(sql.contains("sibling.package_unit <> 'milliliter'"));
        assertFalse(sql.contains("food_item_serving_options"));
        assertFalse(sql.contains("gram_weight"));
        assertFalse(sql.contains("display_name ="));
        assertFalse(sql.contains("barcode ="));
        assertFalse(sql.contains("source_key ="));
        assertFalse(sql.contains("calories ="));
    }
}
