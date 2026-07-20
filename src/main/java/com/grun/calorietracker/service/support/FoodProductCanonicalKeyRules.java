package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.MarketRegion;

import java.text.Normalizer;
import java.util.Locale;

public final class FoodProductCanonicalKeyRules {

    private FoodProductCanonicalKeyRules() {
    }

    public static String resolve(FoodItemEntity product) {
        if (product == null) {
            return null;
        }
        String identityName = FoodProductNormalizationRules.normalizeText(product.getDisplayName());
        if (identityName == null) {
            identityName = FoodProductNormalizationRules.normalizeText(product.getName());
        }
        return resolve(product.getCatalogType(), product.getMarketRegion(), product.getPreparationState(), identityName);
    }

    public static String resolve(
            FoodCatalogType catalogType,
            MarketRegion marketRegion,
            FoodPreparationState preparationState,
            String displayName
    ) {
        if (catalogType != FoodCatalogType.GENERIC_INGREDIENT || displayName == null) {
            return null;
        }
        MarketRegion effectiveRegion = marketRegion == null ? MarketRegion.GLOBAL : marketRegion;
        FoodPreparationState effectiveState = preparationState == null
                ? FoodPreparationState.UNSPECIFIED
                : preparationState;
        return effectiveRegion.name()
                + ":" + catalogType.name()
                + ":" + effectiveState.name()
                + ":" + slug(normalizeCanonicalGenericName(displayName));
    }

    private static String normalizeCanonicalGenericName(String displayName) {
        String withoutPreparation = displayName.replaceAll(
                "(?i)\\b(raw|cooked|boiled|grilled|fried|baked|roasted|steamed|prepared)\\b",
                " "
        );
        return singularizeSimpleFoodName(FoodProductNormalizationRules.normalizeText(withoutPreparation));
    }

    private static String singularizeSimpleFoodName(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.length() < 4) {
            return normalized;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.endsWith("ies") || lower.endsWith("ss") || lower.endsWith("us")) {
            return normalized;
        }
        return lower.endsWith("s") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static String slug(String value) {
        String ascii = Normalizer.normalize(value == null ? "unnamed" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (slug.isBlank()) {
            return "unnamed";
        }
        return slug.length() <= 160 ? slug : slug.substring(0, 160);
    }
}
