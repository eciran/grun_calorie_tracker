package com.grun.calorietracker.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Canonical dietary preference selected during onboarding.")
public enum DietaryPreference {
    NO_PREFERENCE,
    BALANCED,
    HIGH_PROTEIN,
    LOW_CARB,
    VEGETARIAN,
    VEGAN,
    KETO,
    MEDITERRANEAN
}
