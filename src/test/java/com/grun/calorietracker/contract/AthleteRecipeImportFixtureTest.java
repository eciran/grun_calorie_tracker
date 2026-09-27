package com.grun.calorietracker.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.AdminRecipeImportBatchRequestDto;
import com.grun.calorietracker.enums.PreferredLanguage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AthleteRecipeImportFixtureTest {

    @Test
    void athleteImportFixture_matchesCurrentBilingualRecipeContract() throws Exception {
        Path fixture = Path.of("data", "recipe-import", "generated", "grun-athlete-recipes-stage-20.json");
        AdminRecipeImportBatchRequestDto batch = new ObjectMapper().findAndRegisterModules()
                .readValue(Files.readString(fixture), AdminRecipeImportBatchRequestDto.class);

        assertEquals(20, batch.getRecipes().size());
        batch.getRecipes().forEach(candidate -> {
            assertFalse(candidate.getRecipe().getIngredients().isEmpty());
            assertFalse(candidate.getRecipe().getMarketRegions().isEmpty());
            assertEquals(Set.of(PreferredLanguage.EN, PreferredLanguage.TR),
                    candidate.getRecipe().getTranslations().stream()
                            .map(translation -> translation.getLanguage())
                            .collect(Collectors.toSet()));
            candidate.getRecipe().getTranslations().forEach(translation -> {
                assertFalse(translation.getName().isBlank());
                assertFalse(translation.getCookingSteps().isEmpty());
            });
        });
    }
}
