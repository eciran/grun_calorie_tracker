package com.grun.calorietracker.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Normalized allergen codes derived from recipe ingredients or assigned by admin/user review.")
public enum RecipeAllergen {
    MILK,
    EGGS,
    FISH,
    CRUSTACEAN_SHELLFISH,
    TREE_NUTS,
    PEANUTS,
    WHEAT,
    SOYBEANS,
    SESAME,
    GLUTEN,
    CELERY,
    MUSTARD,
    LUPIN,
    MOLLUSCS,
    SULPHITES
}
