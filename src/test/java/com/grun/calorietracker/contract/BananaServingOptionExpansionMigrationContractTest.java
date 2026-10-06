package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BananaServingOptionExpansionMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V302__expand_canonical_banana_serving_options.sql");

    @Test
    void targetsOnlyCanonicalRawGenericBananaAndAddsLocalizedSizeOptions() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("catalog_type = 'GENERIC_INGREDIENT'"));
        assertTrue(sql.contains("preparation_state = 'RAW'"));
        assertTrue(sql.contains("GLOBAL:GENERIC_INGREDIENT:RAW:banana"));
        assertTrue(sql.contains("'1 small banana', 101.0"));
        assertTrue(sql.contains("'1 medium banana', 118.0"));
        assertTrue(sql.contains("'1 large banana', 136.0"));
        assertTrue(sql.contains("'1 küçük boy muz'"));
        assertTrue(sql.contains("'1 orta boy muz'"));
        assertTrue(sql.contains("'1 büyük boy muz'"));
        assertTrue(sql.contains("ON CONFLICT (serving_option_id, language)"));
    }

    @Test
    void avoidsNameMatchingAndRedundantFractionOptions() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertFalse(sql.contains("display_name"));
        assertFalse(sql.contains("lower(fi.name)"));
        assertFalse(sql.contains("1/2 banana"));
        assertFalse(sql.contains("1/4 banana"));
    }
}

