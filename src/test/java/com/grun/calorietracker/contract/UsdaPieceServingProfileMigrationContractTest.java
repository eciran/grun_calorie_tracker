package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsdaPieceServingProfileMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V304__reconcile_usda_piece_serving_profiles.sql");

    @Test
    void freezesSourceKeysConversionsLocalizationsAndQuarantineRules() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("USDA_FOODDATA:fdc:1105314"));
        assertTrue(sql.contains("USDA_FOODDATA:fdc:325430"));
        assertTrue(sql.contains("USDA_FOODDATA:fdc:171287"));
        assertTrue(sql.contains("USDA_FOODDATA:fdc:169303"));
        assertTrue(sql.contains("USDA_FOODDATA:fdc:170049"));
        assertTrue(sql.contains("USDA_FOODDATA:fdc:2747659"));
        assertTrue(sql.contains("quality_status = 'NEEDS_REVIEW'"));
        assertTrue(sql.contains("ON CONFLICT (serving_option_id, language)"));
        assertTrue(sql.contains("'migration-v304'"));
        assertFalse(sql.contains("'migration-v303'"));
        assertTrue(sql.contains("1 küçük boy muz"));
        assertTrue(sql.contains("1 su bardağı pişmiş yumurta"));
    }

    @Test
    void freezesSixtyOptionsWithExactlyOneDefaultPerSourceIdentity() throws Exception {
        String sql = Files.readString(MIGRATION);
        String profileBlock = sql.substring(0, sql.indexOf("-- Remove unsupported options"));
        Set<String> sourceKeys = new HashSet<>();
        Map<String, Integer> defaultsBySource = new HashMap<>();
        int optionCount = 0;

        for (String line : profileBlock.lines().map(String::trim).toList()) {
            if (!line.startsWith("('USDA_FOODDATA:fdc:")) {
                continue;
            }
            optionCount++;
            String sourceKey = line.substring(2, line.indexOf("','"));
            sourceKeys.add(sourceKey);
            if (line.contains(",TRUE,")) {
                defaultsBySource.merge(sourceKey, 1, Integer::sum);
            }
        }

        assertEquals(60, optionCount);
        assertEquals(24, sourceKeys.size());
        assertEquals(sourceKeys, defaultsBySource.keySet());
        assertTrue(defaultsBySource.values().stream().allMatch(count -> count == 1));
    }

    @Test
    void neverMatchesProductsByDisplayName() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();
        assertFalse(sql.contains("where lower(display_name)"));
        assertFalse(sql.contains("like '%banana%'"));
        assertFalse(sql.contains("ai_estimated"));
    }
}

