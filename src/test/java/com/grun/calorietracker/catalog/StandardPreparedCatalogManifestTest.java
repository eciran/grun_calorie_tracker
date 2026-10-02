package com.grun.calorietracker.catalog;

import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodServingOptionUnit;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardPreparedCatalogManifestTest {

    private static final Path DATA_DIR = Path.of("sample-data");

    @Test
    void manifestHasExactlyTwoHundredUniqueItemsAndKnownProfiles() throws IOException {
        List<Map<String, String>> manifest = readCsv("standard-prepared-catalog-200-v1.csv");
        Map<String, Map<String, String>> profiles = indexBy(
                readCsv("standard-prepared-portion-profiles-v1.csv"), "profile_key");

        assertEquals(200, manifest.size());
        assertEquals(200, manifest.stream().map(row -> row.get("item_key")).collect(Collectors.toSet()).size());
        assertEquals(32, profiles.size());
        assertTrue(manifest.stream().allMatch(row -> profiles.containsKey(row.get("portion_profile"))));
        assertTrue(manifest.stream().allMatch(row -> "PLANNED_RESEARCH".equals(row.get("status"))),
                "Scope rows must not become import-ready before nutrition and recipe evidence is attached.");
        assertEquals(FoodCatalogType.STANDARD_PREPARED_ITEM,
                FoodCatalogType.valueOf("STANDARD_PREPARED_ITEM"));
    }

    @Test
    void liquidProfilesUsePerHundredMillilitersAndHavePositiveServingOptions() throws IOException {
        List<Map<String, String>> profiles = readCsv("standard-prepared-portion-profiles-v1.csv");
        List<Map<String, String>> options = readCsv("standard-prepared-liquid-serving-options-v1.csv");
        Set<String> liquidProfiles = profiles.stream()
                .filter(row -> "LIQUID_ML".equals(row.get("measurement_basis")))
                .peek(row -> {
                    assertEquals("PER_100ML", row.get("nutrition_reference_unit"));
                    assertEquals("PROFILE_OPTIONS", row.get("serving_strategy"));
                    assertEquals("false", row.get("item_measurement_required"));
                })
                .map(row -> row.get("profile_key"))
                .collect(Collectors.toSet());

        assertFalse(liquidProfiles.isEmpty());
        assertTrue(options.stream().allMatch(row -> liquidProfiles.contains(row.get("profile_key"))));
        assertTrue(options.stream().allMatch(row -> Double.parseDouble(row.get("ml_volume")) > 0));
        assertTrue(options.stream().allMatch(row -> Double.parseDouble(row.get("quantity")) > 0));
        assertTrue(options.stream().allMatch(row -> isSupportedServingUnit(row.get("unit_type"))));
        assertEquals(options.size(), options.stream()
                .map(row -> row.get("profile_key") + "|" + row.get("market_scope") + "|" + row.get("option_key"))
                .collect(Collectors.toSet()).size());
        assertTrue(liquidProfiles.stream().allMatch(profile -> options.stream()
                .anyMatch(row -> profile.equals(row.get("profile_key")) && "true".equals(row.get("is_default")))));
    }

    @Test
    void solidProfilesRequireItemSpecificWeightEvidence() throws IOException {
        List<Map<String, String>> profiles = readCsv("standard-prepared-portion-profiles-v1.csv");

        List<Map<String, String>> solidProfiles = profiles.stream()
                .filter(row -> "ITEM_GRAMS".equals(row.get("measurement_basis")))
                .toList();

        assertFalse(solidProfiles.isEmpty());
        assertTrue(solidProfiles.stream().allMatch(row -> "PER_100G".equals(row.get("nutrition_reference_unit"))));
        assertTrue(solidProfiles.stream().allMatch(row -> "ITEM_EVIDENCE".equals(row.get("serving_strategy"))));
        assertTrue(solidProfiles.stream().allMatch(row -> "true".equals(row.get("item_measurement_required"))));
    }

    @Test
    void everyManifestModifierHasAnExplicitCalculationRule() throws IOException {
        List<Map<String, String>> manifest = readCsv("standard-prepared-catalog-200-v1.csv");
        Map<String, Map<String, String>> rules = indexBy(
                readCsv("standard-prepared-modifier-rules-v1.csv"), "modifier_key");
        Set<String> usedModifiers = manifest.stream()
                .flatMap(row -> Arrays.stream(row.get("variant_policy").split("\\|")))
                .collect(Collectors.toSet());

        assertTrue(rules.keySet().containsAll(usedModifiers),
                () -> "Missing modifier rules: " + difference(usedModifiers, rules.keySet()));
        assertTrue(rules.values().stream().allMatch(row -> !row.get("calculation_mode").isBlank()));
        assertTrue(rules.values().stream().allMatch(row -> Set.of(
                "READY", "REQUIRES_COMPONENT_LINK", "REQUIRES_PRODUCT_EVIDENCE", "REQUIRES_RECIPE_VARIANT"
        ).contains(row.get("status"))));
    }

    private boolean isSupportedServingUnit(String value) {
        try {
            FoodServingOptionUnit.valueOf(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private Map<String, Map<String, String>> indexBy(List<Map<String, String>> rows, String key) {
        return rows.stream().collect(Collectors.toMap(row -> row.get(key), Function.identity()));
    }

    private List<Map<String, String>> readCsv(String fileName) throws IOException {
        List<String> lines = Files.readAllLines(DATA_DIR.resolve(fileName));
        assertFalse(lines.isEmpty(), fileName + " must not be empty");
        String[] headers = lines.get(0).split(",", -1);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) {
                continue;
            }
            String[] values = lines.get(index).split(",", -1);
            assertEquals(headers.length, values.length,
                    fileName + " row " + (index + 1) + " has an invalid column count");
            Map<String, String> row = new HashMap<>();
            for (int column = 0; column < headers.length; column++) {
                row.put(headers[column], values[column]);
            }
            rows.add(row);
        }
        return rows;
    }
}
