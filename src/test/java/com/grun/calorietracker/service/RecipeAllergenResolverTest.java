package com.grun.calorietracker.service;

import com.grun.calorietracker.enums.RecipeAllergen;
import com.grun.calorietracker.service.support.RecipeAllergenResolver;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeAllergenResolverTest {

    @Test
    void resolve_detectsAllergensInsideHumanReadableFoodNames() {
        Set<RecipeAllergen> result = RecipeAllergenResolver.resolve(
                "Greek yogurt bowl with peanut butter and tahini");

        assertTrue(result.contains(RecipeAllergen.MILK));
        assertTrue(result.contains(RecipeAllergen.PEANUTS));
        assertTrue(result.contains(RecipeAllergen.SESAME));
    }

    @Test
    void resolve_keepsExactDelimitedAllergenMapping() {
        assertEquals(Set.of(RecipeAllergen.WHEAT, RecipeAllergen.SOYBEANS),
                RecipeAllergenResolver.resolve("wheat;soy"));
    }
}
