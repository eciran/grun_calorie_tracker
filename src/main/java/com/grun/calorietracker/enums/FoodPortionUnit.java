package com.grun.calorietracker.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum FoodPortionUnit {
    GRAM,
    MILLILITER,
    TABLESPOON,
    TEASPOON,
    SLICE,
    SERVING,
    PIECE;

    @JsonCreator
    public static FoodPortionUnit fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim()
                .replace(".", "")
                .replace("-", "_")
                .replace(" ", "_")
                .toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "G", "GR", "GRAM", "GRAMS" -> GRAM;
            case "ML", "MILLILITER", "MILLILITERS", "MILLILITRE", "MILLILITRES" -> MILLILITER;
            case "TBSP", "TBS", "TABLESPOON", "TABLESPOONS" -> TABLESPOON;
            case "TSP", "TEASPOON", "TEASPOONS" -> TEASPOON;
            case "SLICE", "SLICES" -> SLICE;
            case "SERVING", "SERVINGS", "PORTION", "PORTIONS" -> SERVING;
            case "PC", "PCS", "PIECE", "PIECES", "UNIT", "UNITS" -> PIECE;
            default -> FoodPortionUnit.valueOf(normalized);
        };
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}