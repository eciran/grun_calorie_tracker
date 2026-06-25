package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodPortionUnit;

import java.util.List;
import java.util.Locale;

public final class FoodPortionUnitResolver {

    private static final List<FoodPortionUnit> LIQUID_UNITS = List.of(FoodPortionUnit.MILLILITER, FoodPortionUnit.TABLESPOON, FoodPortionUnit.TEASPOON, FoodPortionUnit.SERVING);
    private static final List<FoodPortionUnit> COUNTABLE_UNITS = List.of(FoodPortionUnit.PIECE, FoodPortionUnit.SLICE, FoodPortionUnit.GRAM, FoodPortionUnit.TABLESPOON, FoodPortionUnit.TEASPOON, FoodPortionUnit.SERVING);
    private static final List<FoodPortionUnit> DEFAULT_SOLID_UNITS = List.of(FoodPortionUnit.GRAM, FoodPortionUnit.SLICE, FoodPortionUnit.TABLESPOON, FoodPortionUnit.TEASPOON, FoodPortionUnit.SERVING);

    private static final List<String> LIQUID_KEYWORDS = List.of(
            "water", "milk", "juice", "drink", "beverage", "smoothie", "shake", "soda", "cola",
            "tea", "coffee", "soup", "broth", "sauce", "oil", "vinegar", "ayran", "kefir",
            "sut", "sut", "su", "meyve suyu", "corba", "corba", "icecek", "icecek"
    );

    private static final List<String> COUNTABLE_KEYWORDS = List.of(
            "egg", "eggs", "banana", "apple", "orange", "bar", "biscuit", "cookie", "slice",
            "adet", "yumurta", "muz", "elma", "portakal"
    );

    private FoodPortionUnitResolver() {
    }

    public static List<FoodPortionUnit> allowedUnits(FoodItemEntity product) {
        if (isLiquid(product)) {
            return LIQUID_UNITS;
        }
        if (isCountable(product)) {
            return COUNTABLE_UNITS;
        }
        return DEFAULT_SOLID_UNITS;
    }

    public static FoodPortionUnit defaultUnit(FoodItemEntity product) {
        return allowedUnits(product).get(0);
    }

    private static boolean isLiquid(FoodItemEntity product) {
        String servingUnit = normalize(product == null ? null : product.getServingUnit());
        if (servingUnit.equals("ml") || servingUnit.equals("milliliter") || servingUnit.equals("millilitre")
                || servingUnit.equals("l") || servingUnit.equals("liter") || servingUnit.equals("litre")) {
            return true;
        }
        String text = searchableText(product);
        return containsAny(text, LIQUID_KEYWORDS);
    }

    private static boolean isCountable(FoodItemEntity product) {
        if (product != null && product.getCatalogType() == FoodCatalogType.LOCAL_DISH) {
            return false;
        }
        String servingUnit = normalize(product == null ? null : product.getServingUnit());
        if (servingUnit.equals("piece") || servingUnit.equals("pcs") || servingUnit.equals("pc") || servingUnit.equals("adet")) {
            return true;
        }
        String text = searchableText(product);
        return containsAny(text, COUNTABLE_KEYWORDS);
    }

    private static String searchableText(FoodItemEntity product) {
        if (product == null) {
            return "";
        }
        return normalize(String.join(" ", nullToEmpty(product.getName()), nullToEmpty(product.getBrand()), nullToEmpty(product.getSourceKey())));
    }

    private static boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    private static String normalize(String value) {
        return nullToEmpty(value).toLowerCase(Locale.ROOT).trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}