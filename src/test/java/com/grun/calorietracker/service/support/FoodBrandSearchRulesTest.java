package com.grun.calorietracker.service.support;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodBrandSearchRulesTest {
    @Test
    void commonBrandSeparatorsHaveSameKey() {
        for (String brand : new String[]{"Vit Hit", "Vit-Hit", "Vit\u2022Hit", "VitHit", "Vit\u00a0Hit", "Vit\u2013Hit"}) {
            assertEquals("vithit", FoodBrandSearchRules.key(brand));
        }
    }

    @Test
    void numericPercentageAndProductMeaningAreNotRemoved() {
        assertNotEquals(FoodBrandSearchRules.key("Milk 1.5%"), FoodBrandSearchRules.key("Milk 15%"));
        assertNotEquals(FoodBrandSearchRules.key("VitHit"), FoodBrandSearchRules.key("VitHit Perform"));
        assertNull(FoodBrandSearchRules.key("12"));
        assertNull(FoodBrandSearchRules.key("%"));
        assertNull(FoodBrandSearchRules.key(null));
    }

    @Test
    void foldsAccentsAndKeepsLegitimateTwoLetterBrands() {
        assertEquals("nestle", FoodBrandSearchRules.key("Nestlé"));
        assertEquals("clementfaugier", FoodBrandSearchRules.key("Clément Faugier"));
        assertEquals("gu", FoodBrandSearchRules.key("Gü"));
        assertEquals("kp", FoodBrandSearchRules.key("KP"));
        assertEquals("ka", FoodBrandSearchRules.key("KA"));
    }
}
