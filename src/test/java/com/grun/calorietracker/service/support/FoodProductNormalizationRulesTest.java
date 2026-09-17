package com.grun.calorietracker.service.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodProductNormalizationRulesTest {

    @Test
    void reviewedBrandSpellingsHaveOneStableDisplayName() {
        for (String spelling : List.of("Vithit", "Vit Hit", "Vit-Hit", "Vit\u2022Hit",
                "vit\u00b7hit", " VIT  HIT ", "Vit\u00a0Hit", "VITHIT")) {
            assertEquals("VITHIT", FoodProductNormalizationRules.normalizeBrandDisplayName(spelling));
        }
    }

    @Test
    void brandAliasesDoNotCollapseSubbrandsOrMultipleBrands() {
        for (String distinct : List.of("Vit Hit Perform", "Vit Hit, Other Brand", "Vit/Hit",
                "A-B", "AB", "Milk 1.5%", "Milk 15%")) {
            String display = FoodProductNormalizationRules.normalizeBrandDisplayName(distinct);
            assertTrue(!"VITHIT".equals(display));
            assertEquals(display, FoodProductNormalizationRules.normalizeBrandDisplayName(display));
        }
    }

    @Test
    void brandNormalizationPreservesMissingValues() {
        assertEquals(null, FoodProductNormalizationRules.normalizeBrandDisplayName(null));
        assertEquals(null, FoodProductNormalizationRules.normalizeBrandDisplayName("   "));
    }

    @Test
    void reviewedNumericBrandNamesKeepOfficialCasing() {
        for (String spelling : List.of("7up", "7 Up", "7UP")) {
            assertEquals("7UP", FoodProductNormalizationRules.normalizeBrandDisplayName(spelling));
        }
        for (String spelling : List.of("7days", "7 Days", "7DAYS")) {
            assertEquals("7DAYS", FoodProductNormalizationRules.normalizeBrandDisplayName(spelling));
        }
        assertEquals("7 Up Free", FoodProductNormalizationRules.normalizeBrandDisplayName("7 Up Free"));
        assertEquals("7 Days, Other", FoodProductNormalizationRules.normalizeBrandDisplayName("7 Days, Other"));
    }

    @Test
    void stripDiacritics_normalizesTurkishCharacters() {
        assertEquals("tavuk gogsu", FoodProductNormalizationRules.stripDiacritics("tavuk göğsü"));
    }

    @Test
    void expandSearchTerms_addsTurkishAndEnglishSynonyms() {
        List<String> terms = FoodProductNormalizationRules.expandSearchTerms("yogurt");

        assertTrue(terms.contains("yoğurt"));
        assertTrue(terms.contains("yogurt"));
        assertTrue(terms.contains("yoghurt"));
    }

    @Test
    void expandSearchTerms_addsChickenBreastVariants() {
        List<String> terms = FoodProductNormalizationRules.expandSearchTerms("tavuk gogsu");

        assertTrue(terms.contains("tavuk göğsü"));
        assertTrue(terms.contains("tavuk gogsu"));
        assertTrue(terms.contains("chicken breast"));
    }
}
