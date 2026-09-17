package com.grun.calorietracker.service.support;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodBrandRelationshipsTest {
    @Test
    void reviewedCaseAndAbbreviationsAreCanonical() {
        assertEquals("Tesco", normalize("TESCo"));
        assertEquals("Lidl", normalize("LidL"));
        assertEquals("Dunnes Stores", normalize("Dunnes stores"));
        assertEquals("M&S", normalize("Marks & spencer"));
        assertEquals("M&S", normalize("Marks and Spencer"));
        assertEquals("M&S", normalize("M & S"));
    }

    @Test
    void duplicateNamesCollapseButProductLinesSurvive() {
        assertEquals("M&S", normalize("M&S, Marks & Spencer"));
        assertEquals("M&S", normalize("Marks & Spencer, Marks And Spencer"));
        assertEquals("Tesco, Tesco Finest", normalize("TESCo, Tesco finest"));
        assertEquals("M&S Food, M&S", normalize("M&S food, Marks & Spencer"));
    }

    @Test
    void unknownOrAmbiguousListsRemainUntouched() {
        assertEquals("Acme, Inc.", normalize("Acme, Inc."));
        assertEquals("M&S, Unknown Brand", normalize("M&S, Unknown Brand"));
        assertEquals("Tesco,, Tesco", normalize("Tesco,, Tesco"));
        assertEquals("Coop, Co-op", normalize("Coop, Co-op"));
    }

    @Test
    void aliasesRemainSearchableBothWays() {
        assertTrue(FoodProductNormalizationRules.expandSearchTerms("Marks & Spencer").contains("M&S"));
        assertTrue(FoodProductNormalizationRules.expandSearchTerms("M&S").contains("marks & spencer"));
    }

    @Test
    void normalizationIsIdempotent() {
        for (String value : new String[]{"M&S, Marks & Spencer", "Tesco, Tesco finest", "M&S food, Marks & Spencer"}) {
            assertEquals(normalize(value), normalize(normalize(value)));
        }
    }

    private String normalize(String value) {
        return FoodProductNormalizationRules.normalizeBrandDisplayName(value);
    }

    @Test
    void retailerRangesRetainBothComponentsAndOnlyRemoveActualRepeats() {
        assertEquals("Aldi, Specially Selected", normalize("ALdi,Specially selected,Aldi"));
        assertEquals("Lidl, Deluxe", normalize("LidL,deluxe,Lidl"));
        assertEquals("Waitrose, Essential Waitrose", normalize("WaitRose,essential waitrose,Waitrose"));
        assertEquals("Specially Selected, Aldi", normalize("Specially selected,ALdi"));
    }

    @Test
    void rangeQueriesDoNotExpandToBareRetailers() {
        var terms = FoodProductNormalizationRules.expandSearchTerms("Essential Waitrose & Partners");
        assertTrue(terms.contains("Essential Waitrose"));
        assertFalse(terms.stream().anyMatch(term -> term.equalsIgnoreCase("Waitrose")));
        assertFalse(FoodProductNormalizationRules.expandSearchTerms("Deluxe").stream()
                .anyMatch(term -> term.equalsIgnoreCase("Lidl")));
        assertFalse(FoodProductNormalizationRules.expandSearchTerms("Specially Selected").stream()
                .anyMatch(term -> term.equalsIgnoreCase("Aldi")));
    }

    @Test
    void ambiguousAndUnknownRangesAreNotAssignedToRetailers() {
        assertEquals("Deluxe", normalize("Deluxe"));
        assertEquals("Specially Selected", normalize("Specially Selected"));
        assertEquals("Lidl, Unknown Collection", normalize("Lidl, Unknown Collection"));
        assertEquals("Waitrose Duchy Organic", normalize("Waitrose Duchy Organic"));
    }
}
