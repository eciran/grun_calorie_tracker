package com.grun.calorietracker.service.support;

import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;

import java.util.Locale;
import java.util.Set;

public final class FoodLiquidUnitClassifier {

    private static final Set<String> LIQUID_CATEGORIES = Set.of(
            "en:beverages",
            "en:waters",
            "en:juices-and-nectars",
            "en:fruit-juices",
            "en:soft-drinks",
            "en:carbonated-drinks",
            "en:energy-drinks",
            "en:sports-drinks",
            "en:milk-drinks",
            "en:dairy-drinks",
            "en:fermented-drinks",
            "en:fermented-milk-drinks",
            "en:plant-based-beverages",
            "en:coffee-drinks",
            "en:tea-based-beverages",
            "en:iced-teas",
            "en:syrups",
            "en:simple-syrups",
            "tr:içecek",
            "tr:gazlı-içecek"
    );

    private static final Set<String> FORM_CONFLICT_CATEGORIES = Set.of(
            "en:powders",
            "en:drink-powders",
            "en:concentrates",
            "en:ice-creams",
            "en:frozen-desserts",
            "en:pickles"
    );

    private FoodLiquidUnitClassifier() {
    }

    public static Decision classify(Set<String> categoryTags, FoodNutritionReferenceUnit referenceUnit) {
        Set<String> normalized = categoryTags == null
                ? Set.of()
                : categoryTags.stream()
                .filter(java.util.Objects::nonNull)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        if (normalized.stream().anyMatch(FORM_CONFLICT_CATEGORIES::contains)) {
            return Decision.FORM_CONFLICT_REVIEW;
        }
        boolean liquid = normalized.stream().anyMatch(LIQUID_CATEGORIES::contains);
        if (!liquid) {
            return Decision.NOT_ESTABLISHED;
        }
        return referenceUnit == FoodNutritionReferenceUnit.PER_100ML
                ? Decision.ML_SOURCE_SUPPORTED
                : Decision.MASS_VOLUME_BRIDGE_REQUIRED;
    }

    public enum Decision {
        ML_SOURCE_SUPPORTED,
        MASS_VOLUME_BRIDGE_REQUIRED,
        FORM_CONFLICT_REVIEW,
        NOT_ESTABLISHED
    }
}
