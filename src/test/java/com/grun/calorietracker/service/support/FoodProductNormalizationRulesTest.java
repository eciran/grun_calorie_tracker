package com.grun.calorietracker.service.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodProductNormalizationRulesTest {
    @Test
    void tokenVariantsPreserveTurkishSourceAndAsciiAliases() {
        var groups = FoodProductNormalizationRules.searchTokenVariants("Haşlanmış Brokoli");
        assertEquals(2, groups.size());
        assertTrue(groups.get(0).containsAll(List.of("haşlanmış", "haslanmis")));
        assertEquals(java.util.Set.of("brokoli"), groups.get(1));
    }

    @Test void finalQualifierAfterSixWordsMustRemainARequiredToken() {
        var groups = FoodProductNormalizationRules.searchTokenVariants("alpha beta gamma delta epsilon zeta roasted");
        assertEquals(7, groups.size());
        assertEquals(java.util.Set.of("roasted"), groups.get(6));
    }

    @Test
    void tokenVariantsKeepAllSupportedPositionsAndShortTokenContract() {
        assertEquals(7, FoodProductNormalizationRules.searchTokenVariants("alpha beta gamma delta epsilon zeta retained").size());
        assertEquals(List.of(java.util.Set.of("milk")), FoodProductNormalizationRules.searchTokenVariants("a milk"));
        assertEquals(List.of(), FoodProductNormalizationRules.searchTokenVariants(null));
        assertEquals(List.of(), FoodProductNormalizationRules.searchTokenVariants("  "));
    }

    @Test void longQueryLimitsRejectInsteadOfSilentlyTruncating() {
        String words = String.join(" ", java.util.Collections.nCopies(24, "milk"));
        assertEquals(24, FoodProductNormalizationRules.searchTokenVariants(words).size());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> FoodProductNormalizationRules.searchTokenVariants(words + " roasted"));
        assertEquals(1, FoodProductNormalizationRules.searchTokenVariants("a".repeat(256)).size());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> FoodProductNormalizationRules.expandSearchTerms("a".repeat(257)));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> FoodProductNormalizationRules.searchTokenVariants(" ".repeat(257)));
    }

    @Test
    void tokenVariantResultCannotBeMutatedByCandidateSelection() {
        var groups = FoodProductNormalizationRules.searchTokenVariants("chicken meat");
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> groups.add(java.util.Set.of("other")));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> groups.get(0).add("other"));
    }

    @Test
    void unicodeDisplayWhitespaceAndCompositionAreNormalized() {
        assertEquals("Milk 1.5%", FoodProductNormalizationRules.normalizeProductDisplayName("MILK\u00a01.5%"));
        assertEquals("Çiğ Tavuk", FoodProductNormalizationRules.normalizeProductDisplayName("c\u0327ig\u0306 tavuk"));
    }

    @Test
    void unknownBrandSpellingIsNotInventedByTitleCasing() {
        assertEquals("içim", FoodProductNormalizationRules.normalizeBrandDisplayName("içim"));
        assertEquals("ACME foods", FoodProductNormalizationRules.normalizeBrandDisplayName("ACME foods"));
    }

    @Test
    void turkishCasingPreservesDottedAndDotlessLetters() {
        assertEquals("İçim Süt", FoodProductNormalizationRules.normalizeProductDisplayName("İÇİM SÜT", com.grun.calorietracker.enums.PreferredLanguage.TR));
        assertEquals("Ispanak", FoodProductNormalizationRules.normalizeProductDisplayName("ISPANAK", com.grun.calorietracker.enums.PreferredLanguage.TR));
        assertEquals("Izgara Tavuk", FoodProductNormalizationRules.normalizeProductDisplayName("ızgara tavuk", com.grun.calorietracker.enums.PreferredLanguage.TR));
    }

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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"çiğ tavuk göğsü", "soslu tavuk gogsu", "tavuk gogsu soslu", "tavukgogsu", "tavuklu gogus sosu"})
    void partialOrQualifiedChickenPhraseNeverExpandsToUnqualifiedChicken(String query) {
        assertTrue(!FoodProductNormalizationRules.expandSearchTerms(query).contains("chicken breast"));
        assertTrue(!FoodProductNormalizationRules.expandSearchTerms(query).contains("tavuk göğsü"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Marks and Spencer Milk", "M & S Milk", "Vit Hit Apple", "Tesco Finest Milk"})
    void reviewedCompoundBrandsRemainOneImmutableTokenCondition(String query) {
        var groups = FoodProductNormalizationRules.searchTokenVariants(query);
        assertEquals(2, groups.size());
        assertTrue(groups.get(0).stream().anyMatch(token -> token.contains(" ")));
        assertEquals(java.util.Set.of(query.endsWith("Apple") ? "apple" : "milk"), groups.get(1));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> groups.get(0).add("other"));
    }

    @Test void reviewedSubbrandKeysDoNotCollapseToTheirParent() {
        assertTrue(FoodBrandSearchRules.searchKeys("M&S Food").contains("m&sfood"));
        assertTrue(!FoodBrandSearchRules.searchKeys("M&S Food").contains("m&s"));
        assertEquals(java.util.Set.of("tescofinest"), FoodBrandSearchRules.searchKeys("Tesco Finest"));
        assertEquals(java.util.Set.of("vit/hit"), FoodBrandSearchRules.searchKeys("Vit/Hit"));
        assertEquals(java.util.Set.of(), FoodBrandSearchRules.searchKeys(null));
    }
}
