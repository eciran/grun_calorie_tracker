package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.enums.FoodPreparationState;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericFoodManifestGateTest {

    private static final String MANIFEST_RESOURCE = "/generic-food-manifest-v1.json";
    private static final String SOURCE_SELECTION_RESOURCE = "/generic-food-source-selection-v1.json";
    private static final Path QUERY_FILE = Path.of("sample-data", "usda-foundation-foods-queries.txt");
    private static final Set<String> REQUIRED_CATEGORIES = Set.of(
            "FRUIT", "VEGETABLE", "MEAT", "FISH", "DAIRY", "GRAIN",
            "LEGUME", "FAT", "NUT", "SEED", "DRINK"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void genericFoodManifest_isCompleteVersionedAndReproducible() throws Exception {
        JsonNode manifest;
        try (var input = getClass().getResourceAsStream(MANIFEST_RESOURCE)) {
            assertTrue(input != null, "Generic food manifest fixture is missing");
            manifest = objectMapper.readTree(input);
        }

        assertEquals("generic-food-manifest-v1", manifest.path("version").asText());
        assertEquals("GLOBAL", manifest.path("marketRegion").asText());
        assertEquals("GENERIC_INGREDIENT", manifest.path("catalogType").asText());
        assertEquals(1, manifest.path("sourceSelection").path("maxRecordsPerVariant").asInt());
        assertTrue(manifest.path("sourceSelection").path("requireHumanApprovalBeforeProduction").asBoolean());
        assertEquals("fdc_id", manifest.path("sourceSelection").path("stableIdentifier").asText());
        assertEquals("SOURCE_REPORTED", manifest.path("nutritionPolicy").path("basis").asText());
        assertEquals("ESTIMATED", manifest.path("localDishPolicy").path("estimatedValuesRequireBasis").asText());
        assertEquals("CALCULATED", manifest.path("localDishPolicy").path("calculatedValuesRequireBasis").asText());

        Set<String> requiredNutrition = jsonTextSet(manifest.path("nutritionPolicy").path("requiredFields"));
        assertEquals(Set.of("calories", "protein", "fat", "carbs"), requiredNutrition);
        assertTrue(manifest.path("nutritionPolicy").path("requiredOptionalFieldCount").asInt() >= 1);

        JsonNode servingProfiles = manifest.path("servingProfiles");
        Set<String> identities = new HashSet<>();
        Set<String> queries = new HashSet<>();
        List<String> orderedQueries = new ArrayList<>();
        Map<String, Integer> categoryCounts = new HashMap<>();
        int variantCount = 0;

        for (JsonNode entry : manifest.path("entries")) {
            String identity = requiredText(entry, "identity");
            assertTrue(identity.matches("[a-z0-9_]+"), "Invalid identity: " + identity);
            assertTrue(identities.add(identity), "Duplicate identity: " + identity);

            String category = requiredText(entry, "category");
            assertTrue(REQUIRED_CATEGORIES.contains(category), "Unsupported category: " + category);
            categoryCounts.merge(category, 1, Integer::sum);

            assertEquals("USDA_FOODDATA", requiredText(entry, "authoritativeSource"));
            assertLocalizedText(entry.path("displayNames"), identity);
            assertLocalizedAliases(entry.path("aliases"), identity);

            String servingProfile = requiredText(entry, "servingProfile");
            assertTrue(servingProfiles.has(servingProfile), "Unknown serving profile for " + identity);
            assertLocalizedText(servingProfiles.path(servingProfile).path("labels"), "serving profile " + servingProfile);

            JsonNode variants = entry.path("variants");
            assertTrue(variants.isArray() && !variants.isEmpty(), "Missing variants for " + identity);
            Set<String> states = new HashSet<>();
            for (JsonNode variant : variants) {
                String state = requiredText(variant, "state");
                FoodPreparationState.valueOf(state);
                assertTrue(states.add(state), "Duplicate preparation state for " + identity + ": " + state);
                String query = requiredText(variant, "query");
                assertTrue(queries.add(query), "Duplicate USDA query: " + query);
                orderedQueries.add(query);
                JsonNode serving = variant.path("serving");
                assertFalse(requiredText(serving, "unitType").isBlank());
                assertTrue(serving.path("gramWeight").asDouble() > 0 || serving.path("mlVolume").asDouble() > 0,
                        "Serving conversion is missing for " + identity + " " + state);
                assertLocalizedText(serving.path("labels"), identity + " " + state + " serving");
                variantCount++;
            }
        }

        assertEquals(96, identities.size());
        assertEquals(146, variantCount);
        assertEquals(REQUIRED_CATEGORIES, categoryCounts.keySet());
        manifest.path("categoryMinimums").fields().forEachRemaining(minimum -> {
            int actual = categoryCounts.getOrDefault(minimum.getKey(), 0);
            assertTrue(actual >= minimum.getValue().asInt(),
                    minimum.getKey() + " coverage " + actual + " is below " + minimum.getValue().asInt());
        });

        List<String> generatedQueries = Files.readAllLines(QUERY_FILE, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        assertEquals(orderedQueries, generatedQueries, "USDA query file must be generated from the manifest");

        JsonNode sourceSelection;
        try (var input = getClass().getResourceAsStream(SOURCE_SELECTION_RESOURCE)) {
            assertTrue(input != null, "Generic food source selection fixture is missing");
            sourceSelection = objectMapper.readTree(input);
        }
        assertEquals("generic-food-source-selection-v1", sourceSelection.path("version").asText());
        assertEquals(manifest.path("version").asText(), sourceSelection.path("manifestVersion").asText());
        assertEquals(variantCount, sourceSelection.path("selectionCount").asInt());

        Set<String> selectedQueries = new HashSet<>();
        Set<String> selectedSourceKeys = new HashSet<>();
        for (JsonNode selection : sourceSelection.path("entries")) {
            String query = requiredText(selection, "query");
            assertTrue(selectedQueries.add(query), "Duplicate selected query: " + query);
            String sourceKey = requiredText(selection, "sourceKey");
            assertTrue(sourceKey.startsWith("USDA_FOODDATA:fdc:"), "Unexpected source key: " + sourceKey);
            assertTrue(selectedSourceKeys.add(sourceKey), "Duplicate selected source key: " + sourceKey);
            assertFalse(requiredText(selection, "selectedName").isBlank());
            assertEquals("SOURCE_REPORTED", requiredText(selection, "nutritionBasis"));
            FoodPreparationState expected = FoodPreparationState.valueOf(requiredText(selection, "expectedState"));
            FoodPreparationState selected = FoodPreparationState.valueOf(requiredText(selection, "selectedPreparationState"));
            assertTrue(preparationCompatible(expected, selected),
                    "Preparation mismatch for " + query + ": expected " + expected + ", selected " + selected);
        }
        assertEquals(queries, selectedQueries, "Every manifest variant must have one approved USDA source");
        assertEquals(variantCount, selectedSourceKeys.size());
        writeReport(categoryCounts, identities.size(), variantCount, queries.size(), selectedSourceKeys.size());
    }

    private boolean preparationCompatible(FoodPreparationState expected, FoodPreparationState selected) {
        if (expected == selected || expected == FoodPreparationState.UNSPECIFIED) {
            return true;
        }
        if (selected == FoodPreparationState.UNSPECIFIED
                && Set.of(FoodPreparationState.RAW, FoodPreparationState.PREPARED).contains(expected)) {
            return true;
        }
        return expected == FoodPreparationState.COOKED
                && Set.of(
                        FoodPreparationState.COOKED,
                        FoodPreparationState.BOILED,
                        FoodPreparationState.GRILLED,
                        FoodPreparationState.FRIED,
                        FoodPreparationState.BAKED,
                        FoodPreparationState.ROASTED,
                        FoodPreparationState.STEAMED
                ).contains(selected);
    }
    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText().trim();
        assertFalse(value.isBlank(), "Missing " + field);
        assertFalse(value.contains("Ã"), "Mojibake in " + field + ": " + value);
        return value;
    }

    private void assertLocalizedText(JsonNode node, String context) {
        for (String language : List.of("EN", "TR")) {
            String value = node.path(language).asText().trim();
            assertFalse(value.isBlank(), "Missing " + language + " text for " + context);
            assertFalse(value.contains("Ã"), "Mojibake in " + language + " text for " + context);
        }
    }

    private void assertLocalizedAliases(JsonNode node, String identity) {
        for (String language : List.of("EN", "TR")) {
            JsonNode aliases = node.path(language);
            assertTrue(aliases.isArray() && !aliases.isEmpty(), "Missing " + language + " aliases for " + identity);
            for (JsonNode alias : aliases) {
                assertFalse(alias.asText().isBlank(), "Blank alias for " + identity);
                assertFalse(alias.asText().contains("Ã"), "Mojibake alias for " + identity);
            }
        }
    }

    private Set<String> jsonTextSet(JsonNode array) {
        Set<String> values = new HashSet<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private void writeReport(Map<String, Integer> categoryCounts, int entries, int variants, int queries, int selectedSources) throws Exception {
        Path report = Path.of("target", "reports", "generic-food-manifest-v1-report.json");
        Files.createDirectories(report.getParent());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("manifestVersion", "generic-food-manifest-v1");
        payload.put("entries", entries);
        payload.put("variants", variants);
        payload.put("queries", queries);
        payload.put("selectedSources", selectedSources);
        payload.put("categories", new java.util.TreeMap<>(categoryCounts));
        payload.put("status", "PASS");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(report.toFile(), payload);
    }
}