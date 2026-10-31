package com.grun.calorietracker.service.support;

import com.grun.calorietracker.enums.FoodPortionUnit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FoodPackageMeasureRules {
    private static final Pattern TEXT_MEASURE = Pattern.compile(
            "(?i)(?<!\\w)(?:(\\d+)\\s*x\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(kg|gr|g|ml|cl|lt|l)(?!\\w)"
    );

    private FoodPackageMeasureRules() {
    }

    public static PackageMeasure resolve(String numericQuantity, String explicitUnit, String quantityText) {
        PackageMeasure textMeasure = parseText(quantityText);
        boolean hasNumeric = numericQuantity != null && !numericQuantity.isBlank();
        boolean hasUnit = explicitUnit != null && !explicitUnit.isBlank();
        if (!hasNumeric && !hasUnit) {
            return textMeasure;
        }
        if (!hasNumeric || !hasUnit) {
            if (textMeasure != null) {
                if (hasNumeric) {
                    BigDecimal supplied = positive(numericQuantity, "package quantity");
                    if (!close(supplied, textMeasure.quantity())) {
                        throw new IllegalArgumentException("Package quantity conflicts with quantity text");
                    }
                }
                return textMeasure;
            }
            throw new IllegalArgumentException("Package quantity and package unit must be supplied together");
        }

        PackageMeasure explicit = new PackageMeasure(
                normalized(positive(numericQuantity, "package quantity")), normalizeUnit(explicitUnit)
        );
        if (textMeasure != null && (explicit.unit() != textMeasure.unit()
                || !close(explicit.quantity(), textMeasure.quantity()))) {
            throw new IllegalArgumentException("Explicit package metadata conflicts with quantity text");
        }
        return explicit;
    }

    public static PackageMeasure resolveSource(String numericQuantity, String sourceUnit, String quantityText) {
        PackageMeasure textMeasure = parseText(quantityText);
        boolean hasNumeric = numericQuantity != null && !numericQuantity.isBlank();
        boolean hasUnit = sourceUnit != null && !sourceUnit.isBlank();
        if (!hasNumeric || !hasUnit) {
            return textMeasure;
        }
        return resolve(numericQuantity, sourceUnit, quantityText);
    }

    private static PackageMeasure parseText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = TEXT_MEASURE.matcher(value.replace('×', 'x'));
        if (!matcher.find()) {
            return null;
        }
        BigDecimal count = matcher.group(1) == null ? BigDecimal.ONE : new BigDecimal(matcher.group(1));
        BigDecimal amount = positive(matcher.group(2).replace(',', '.'), "package quantity").multiply(count);
        String unit = matcher.group(3).toLowerCase(Locale.ROOT);
        return switch (unit) {
            case "kg" -> new PackageMeasure(normalized(amount.multiply(BigDecimal.valueOf(1000))), FoodPortionUnit.GRAM);
            case "g", "gr" -> new PackageMeasure(normalized(amount), FoodPortionUnit.GRAM);
            case "l", "lt" -> new PackageMeasure(normalized(amount.multiply(BigDecimal.valueOf(1000))), FoodPortionUnit.MILLILITER);
            case "cl" -> new PackageMeasure(normalized(amount.multiply(BigDecimal.TEN)), FoodPortionUnit.MILLILITER);
            case "ml" -> new PackageMeasure(normalized(amount), FoodPortionUnit.MILLILITER);
            default -> throw new IllegalArgumentException("Unsupported package unit: " + unit);
        };
    }

    private static FoodPortionUnit normalizeUnit(String value) {
        FoodPortionUnit unit;
        try {
            unit = FoodPortionUnit.fromJson(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported package unit: " + value, exception);
        }
        if (unit != FoodPortionUnit.GRAM && unit != FoodPortionUnit.MILLILITER) {
            throw new IllegalArgumentException("Package unit must be GRAM or MILLILITER");
        }
        return unit;
    }

    private static BigDecimal positive(String value, String label) {
        try {
            BigDecimal number = new BigDecimal(value.trim().replace(',', '.'));
            if (number.signum() <= 0) {
                throw new IllegalArgumentException(label + " must be positive");
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + label, exception);
        }
    }

    private static BigDecimal normalized(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private static boolean close(BigDecimal left, BigDecimal right) {
        BigDecimal tolerance = left.abs().max(right.abs()).multiply(BigDecimal.valueOf(.01)).max(BigDecimal.ONE);
        return left.subtract(right).abs().compareTo(tolerance) <= 0;
    }

    public record PackageMeasure(BigDecimal quantity, FoodPortionUnit unit) {
    }
}
