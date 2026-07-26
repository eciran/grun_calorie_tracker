package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.DietaryPreference;
import com.grun.calorietracker.enums.RecipeAllergen;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Diet and allergen selections saved during onboarding.")
public class OnboardingNutritionStepDto {

    private DietaryPreference primaryDietaryPreference;

    private Set<RecipeAllergen> allergens;

    private Boolean allergenSelectionConfirmed;

    public boolean isAllergenSelectionConfirmed() {
        return Boolean.TRUE.equals(allergenSelectionConfirmed);
    }
}
