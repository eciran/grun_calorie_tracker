package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodPortionUnit;

import java.util.List;
import java.util.Locale;

public final class FoodPortionUnitResolver {

    private static final List<FoodPortionUnit> LIQUID_UNITS = List.of(FoodPortionUnit.MILLILITER, FoodPortionUnit.SERVING);
    private static final List<FoodPortionUnit> SPOONABLE_UNITS = List.of(FoodPortionUnit.GRAM, FoodPortionUnit.TABLESPOON, FoodPortionUnit.TEASPOON, FoodPortionUnit.SERVING);
    private static final List<FoodPortionUnit> COUNTABLE_UNITS = List.of(FoodPortionUnit.PIECE, FoodPortionUnit.GRAM, FoodPortionUnit.SERVING);
    private static final List<FoodPortionUnit> SLICEABLE_UNITS = List.of(FoodPortionUnit.SLICE, FoodPortionUnit.GRAM, FoodPortionUnit.SERVING);
    private static final List<FoodPortionUnit> DEFAULT_SOLID_UNITS = List.of(FoodPortionUnit.GRAM, FoodPortionUnit.SERVING);

    private static final List<String> LIQUID_KEYWORDS = List.of(
            "water", "milk", "juice", "drink", "beverage", "smoothie", "shake", "soda", "cola",
            "tea", "coffee", "soup", "broth", "ayran", "kefir", "sut", "su", "meyve suyu",
            "corba", "icecek"
    );

    private static final List<String> SPOONABLE_KEYWORDS = List.of(
            "oil", "olive oil", "vinegar", "sauce", "honey", "jam", "peanut butter", "spread",
            "tahini", "molasses", "syrup", "paste", "yogurt", "yoghurt", "yogurt",
            "zeytinyagi", "zeytin yagi", "bal", "recel", "pekmez", "tahin", "salca", "sos"
    );

    private static final List<String> COUNTABLE_KEYWORDS = List.of(
            "egg", "eggs", "banana", "apple", "orange", "bar", "biscuit", "cookie",
            "adet", "yumurta", "muz", "elma", "portakal"
    );

    private static final List<String> SLICEABLE_KEYWORDS = List.of(
            "bread", "toast", "cheese", "ham", "salami", "cake", "pizza", "slice",
            "ekmek", "peynir", "dilim", "pasta", "borek"
    );

    private FoodPortionUnitResolver() {
    }

    public static List<FoodPortionUnit> allowedUnits(FoodItemEntity product) {
        if (isLiquid(product)) {
            return LIQUID_UNITS;
        }
        if (isSpoonable(product)) {
            return SPOONABLE_UNITS;
        }
        if (isCountable(product)) {
            return COUNTABLE_UNITS;
        }
        if (isSliceable(product)) {
            return SLICEABLE_UNITS;
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

    private static boolean isSpoonable(FoodItemEntity product) {
        String text = searchableText(product);
        return containsAny(text, SPOONABLE_KEYWORDS);
    }

    private static boolean isSliceable(FoodItemEntity product) {
        String servingUnit = normalize(product == null ? null : product.getServingUnit());
        if (servingUnit.equals("slice") || servingUnit.equals("slices") || servingUnit.equals("dilim")) {
            return true;
        }
        String text = searchableText(product);
        return containsAny(text, SLICEABLE_KEYWORDS);
    }

    private static String searchableText(FoodItemEntity product) {
        if (product == null) {
            return "";
        }
        return normalize(String.join(" ", nullToEmpty(product.getName()), nullToEmpty(product.getBrand()), nullToEmpty(product.getSourceKey())));
    }

    private static boolean containsAny(String text, List<String> keywords) {
        String searchable = " " + text + " ";
        return keywords.stream().anyMatch(keyword -> searchable.contains(" " + normalize(keyword) + " "));
    }

    private static String normalize(String value) {
        return nullToEmpty(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}