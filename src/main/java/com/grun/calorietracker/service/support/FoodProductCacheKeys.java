package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.FoodSearchCriteriaDto;

public final class FoodProductCacheKeys {

    private FoodProductCacheKeys() {
    }

    public static String barcode(String barcode) {
        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(barcode);
        return normalizedBarcode == null ? "invalid" : normalizedBarcode;
    }

    public static String search(FoodSearchCriteriaDto criteria, int page, int size) {
        FoodSearchCriteriaDto safeCriteria = criteria == null ? new FoodSearchCriteriaDto() : criteria;
        return String.join(":",
                String.valueOf(Math.max(page, 0)),
                String.valueOf(Math.max(size, 1)),
                part(FoodProductNormalizationRules.normalizeText(safeCriteria.getQuery())),
                part(safeCriteria.getMarketRegion()),
                part(safeCriteria.getCatalogType()),
                part(FoodProductNormalizationRules.normalizeText(safeCriteria.getBrand())),
                part(safeCriteria.getMinCalories()),
                part(safeCriteria.getMaxCalories()),
                part(FoodProductNormalizationRules.normalizeText(safeCriteria.getSortBy())),
                part(FoodProductNormalizationRules.normalizeText(safeCriteria.getSortOrder())),
                part(FoodProductNormalizationRules.normalizeText(safeCriteria.getNutriScore()))
        );
    }

    private static String part(Object value) {
        return value == null ? "_" : value.toString().trim().toLowerCase(java.util.Locale.ROOT).replace(':', '_');
    }
}