package com.grun.calorietracker.service.support;

public record NormalizedFoodPortion(
        Double grams,
        Double milliliters,
        Double nutritionReferenceAmount
) {
}
