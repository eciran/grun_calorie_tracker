package com.grun.calorietracker.service.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodProductNormalizationRulesTest {

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
