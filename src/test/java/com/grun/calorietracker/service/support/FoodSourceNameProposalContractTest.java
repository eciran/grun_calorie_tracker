package com.grun.calorietracker.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemLocalizationEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.PreferredLanguage;
import java.io.IOException;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

/** Formatting acceptance of offline proposals is not runtime/source approval. */
class FoodSourceNameProposalContractTest {
    private JsonNode registry() throws IOException {
        try (var input = getClass().getResourceAsStream("/catalog/source-name-proposals-r5-f4-v1.json")) {
            assertNotNull(input);
            return new ObjectMapper().readTree(input);
        }
    }

    @TestFactory
    Stream<DynamicTest> everyProposedNameSurvivesBackendNormalization() throws IOException {
        return StreamSupport.stream(registry().path("entries").spliterator(), false).flatMap(row ->
                Stream.of(PreferredLanguage.EN, PreferredLanguage.TR).map(language -> DynamicTest.dynamicTest(
                        row.get(0).asText() + "-" + language,
                        () -> {
                            String label = row.get(language == PreferredLanguage.EN ? 2 : 3).asText();
                            assertEquals(label, FoodProductNormalizationRules.normalizeProductDisplayName(label, language));
                            assertTrue(Normalizer.isNormalized(label, Normalizer.Form.NFC));
                            assertTrue(label.length() <= 255);
                        })));
    }

    @Test
    void allPairsPassExistingDisplayAndLanguageAuditWithoutChangingFacts() throws IOException {
        for (var row : registry().path("entries")) {
            var food = new FoodItemEntity();
            food.setName(row.get(1).asText());
            food.setDisplayName(row.get(2).asText());
            food.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
            var en = localization(PreferredLanguage.EN, row.get(2).asText());
            var tr = localization(PreferredLanguage.TR, row.get(3).asText());
            assertTrue(FoodCatalogNamingRules.findings(food, List.of(en, tr)).isEmpty(),
                    () -> row.get(0).asText() + ": " + FoodCatalogNamingRules.findings(food, List.of(en, tr)));
            assertEquals(row.get(1).asText(), food.getName());
            assertNull(food.getCalories());
            assertNull(food.getPreparationState());
        }
    }

    @Test
    void offlineProposalRegistryDoesNotGrantRuntimeExceptions() throws IOException {
        var data = registry();
        assertEquals("OFFLINE_PROPOSALS_ONLY_NOT_RUNTIME_APPROVED", data.path("status").asText());
        assertEquals(118, data.path("entries").size());
        var ids = new HashSet<String>();
        for (var row : data.path("entries")) {
            assertTrue(ids.add(row.get(0).asText()));
            assertTrue(FoodSourceNameRules.reviewedSource("USDA_FOODDATA:fdc:" + row.get(0).asText()).isEmpty());
        }
    }

    private FoodItemLocalizationEntity localization(PreferredLanguage language, String name) {
        var value = new FoodItemLocalizationEntity();
        value.setLanguage(language);
        value.setDisplayName(name);
        value.setShortDisplayName(name);
        value.setActive(true);
        return value;
    }
}
