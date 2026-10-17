package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.enums.PreferredLanguage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FoodProductCacheKeysTest {
    @Test
    void queryPunctuationMustNotCollide() {
        assertNotEquals(key("milk:plain", null), key("milk_plain", null));
    }

    @Test
    void nullBrandMustNotCollideWithLiteralPlaceholder() {
        assertNotEquals(key("milk", null), key("milk", "_"));
    }

    @Test
    void categoryMustParticipateInSearchIdentity() {
        FoodSearchCriteriaDto first = new FoodSearchCriteriaDto();
        FoodSearchCriteriaDto second = new FoodSearchCriteriaDto();
        first.setCategory("dairy");
        second.setCategory("vegetables");
        assertNotEquals(FoodProductCacheKeys.search(first, 0, 25),
                FoodProductCacheKeys.search(second, 0, 25));
    }

    @Test
    void languageAndPaginationRemainDistinct() {
        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("milk");
        criteria.setPreferredLanguage(PreferredLanguage.EN);
        String english = FoodProductCacheKeys.search(criteria, 0, 25);
        criteria.setPreferredLanguage(PreferredLanguage.TR);
        assertNotEquals(english, FoodProductCacheKeys.search(criteria, 0, 25));
        assertNotEquals(FoodProductCacheKeys.search(criteria, 0, 25),
                FoodProductCacheKeys.search(criteria, 1, 25));
        assertNotEquals(FoodProductCacheKeys.search(criteria, 0, 25),
                FoodProductCacheKeys.search(criteria, 0, 50));
    }

    @Test
    void equivalentNormalizedInputsKeepStableKeys() {
        assertEquals(key(" Milk ", " Acme "), key("milk", "acme"));
        assertEquals(FoodProductCacheKeys.search(null, 0, 25),
                FoodProductCacheKeys.search(new FoodSearchCriteriaDto(), 0, 25));
    }

    @Test
    void keyLengthIsBoundedAndOldNamespaceIsNotReused() {
        String shortKey = key("milk", null);
        assertTrue(shortKey.matches("v3:[0-9a-f]{64}"));
        assertEquals(shortKey.length(), key("a".repeat(256), null).length());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> key("milk".repeat(10_000), null));
    }

    @Test
    void paginationBoundariesCannotCollapseIntoSameDigits() {
        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery("milk");
        assertNotEquals(FoodProductCacheKeys.search(criteria, 1, 23),
                FoodProductCacheKeys.search(criteria, 12, 3));
    }

    private String key(String query, String brand) {
        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery(query);
        criteria.setBrand(brand);
        return FoodProductCacheKeys.search(criteria, 0, 25);
    }
}
