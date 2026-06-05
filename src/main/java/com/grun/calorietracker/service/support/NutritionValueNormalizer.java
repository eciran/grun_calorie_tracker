package com.grun.calorietracker.service.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class NutritionValueNormalizer {

    private NutritionValueNormalizer() {
    }

    public static Double calories(Double value) {
        return round(value, 1);
    }

    public static Double macro(Double value) {
        return round(value, 1);
    }

    public static Double sodium(Double value) {
        return round(value, 3);
    }

    public static Double servingSize(Double value) {
        return round(value, 1);
    }

    private static Double round(Double value, int scale) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return value;
        }
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
