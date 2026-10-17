package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.FoodSearchCriteriaDto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class FoodProductCacheKeys {

    private static final String SEARCH_KEY_VERSION = "v3";

    private FoodProductCacheKeys() {
    }

    public static String barcode(String barcode) {
        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(barcode);
        return normalizedBarcode == null ? "invalid" : normalizedBarcode;
    }

    public static String search(FoodSearchCriteriaDto criteria, int page, int size) {
        FoodSearchCriteriaDto safeCriteria = criteria == null ? new FoodSearchCriteriaDto() : criteria;
        FoodProductNormalizationRules.validateSearchQuery(safeCriteria.getQuery());
        String framedCriteria = String.join("",
                part(Math.max(page, 0)),
                part(Math.max(size, 1)),
                part(FoodProductNormalizationRules.normalizeText(safeCriteria.getQuery())),
                part(safeCriteria.getMarketRegion()),
                part(safeCriteria.getCatalogType()),
                part(safeCriteria.getPreparationState()),
                part(safeCriteria.getPreferredLanguage()),
                part(FoodProductNormalizationRules.normalizeText(safeCriteria.getBrand())),
                part(FoodProductNormalizationRules.normalizeText(safeCriteria.getCategory())),
                part(safeCriteria.getCategoryId()),
                part(safeCriteria.getMinCalories()),
                part(safeCriteria.getMaxCalories()),
                part(FoodProductNormalizationRules.normalizeText(safeCriteria.getSortBy())),
                part(FoodProductNormalizationRules.normalizeText(safeCriteria.getSortOrder())),
                part(FoodProductNormalizationRules.normalizeText(safeCriteria.getNutriScore()))
        );
        try {
            return SEARCH_KEY_VERSION + ":" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(framedCriteria.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private static String part(Object value) {
        if (value == null) {
            return "n;";
        }
        String normalized = value.toString().trim().toLowerCase(java.util.Locale.ROOT);
        // Length framing preserves literal punctuation and distinguishes null from user text.
        return "v" + normalized.length() + ":" + normalized + ";";
    }
}
