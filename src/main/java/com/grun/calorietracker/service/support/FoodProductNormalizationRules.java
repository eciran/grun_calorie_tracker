package com.grun.calorietracker.service.support;

import java.util.Locale;

public final class FoodProductNormalizationRules {

    private FoodProductNormalizationRules() {
    }

    public static String normalizeBarcode(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }

        normalized = normalized.replaceAll("[\\s-]+", "");
        if (normalized.isBlank()) {
            return null;
        }

        return normalized.toUpperCase(Locale.ROOT);
    }

    public static String normalizeText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
