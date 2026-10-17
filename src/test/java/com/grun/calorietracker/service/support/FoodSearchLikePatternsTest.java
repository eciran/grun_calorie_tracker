package com.grun.calorietracker.service.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoodSearchLikePatternsTest {
    @Test
    void escapesOnlyLikeMetaCharactersAndTheChosenEscape() {
        assertEquals("a!%b!_c!!d\\e'\u011f?", FoodSearchLikePatterns.literal("a%b_c!d\\e'\u011f?"));
    }

    @Test
    void containsAddsOnlyApplicationOwnedWildcards() {
        assertEquals("%!%milk!_!!%", FoodSearchLikePatterns.contains("%milk_!"));
    }

    @Test
    void normalWordsAndApostrophesArePreserved() {
        assertEquals("%chef's coffee%", FoodSearchLikePatterns.contains("chef's coffee"));
    }
}
