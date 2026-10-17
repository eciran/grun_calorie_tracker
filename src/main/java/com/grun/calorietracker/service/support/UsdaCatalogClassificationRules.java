package com.grun.calorietracker.service.support;

import com.grun.calorietracker.enums.FoodCatalogType;
import java.util.Locale;

/** Source subtype is evidence; an FDC id or a missing GTIN alone is not. */
public final class UsdaCatalogClassificationRules {
    private UsdaCatalogClassificationRules() { }

    public static String normalizeDataType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ')) {
            case "foundation" -> "Foundation";
            case "sr legacy" -> "SR Legacy";
            case "branded" -> "Branded";
            case "survey (fndds)", "survey", "fndds" -> "Survey (FNDDS)";
            case "experimental" -> "Experimental";
            default -> throw new IllegalArgumentException("Unsupported USDA data type: " + value);
        };
    }

    public static FoodCatalogType resolve(String dataType, FoodCatalogType declared, boolean hasBarcode) {
        String normalized = normalizeDataType(dataType);
        if (normalized == null) {
            // Legacy files remain ingestible, but the importer must keep them in review.
            return declared != null ? declared : hasBarcode
                    ? FoodCatalogType.BRANDED_PRODUCT : FoodCatalogType.GENERIC_INGREDIENT;
        }
        if ("Branded".equals(normalized)) {
            if (declared != null && declared != FoodCatalogType.BRANDED_PRODUCT) {
                throw new IllegalArgumentException("USDA Branded conflicts with non-branded catalog_type.");
            }
            return FoodCatalogType.BRANDED_PRODUCT;
        }
        if (hasBarcode || declared == FoodCatalogType.BRANDED_PRODUCT) {
            throw new IllegalArgumentException("USDA non-branded subtype conflicts with GTIN/branded classification.");
        }
        if (declared != null) {
            return declared;
        }
        if ("Foundation".equals(normalized) || "SR Legacy".equals(normalized)) {
            return FoodCatalogType.GENERIC_INGREDIENT;
        }
        throw new IllegalArgumentException("USDA survey/experimental foods require an explicit reviewed catalog_type.");
    }

    public static FoodCatalogType resolve(String dataType, FoodCatalogType declared, boolean hasBarcode,
                                         String sourceKey, String sourceDescription) {
        var namedScope = UsdaNamedBrandRules.match(sourceKey, sourceDescription, dataType);
        if (namedScope.isPresent()) {
            if (!namedScope.get().sourceKey().equals(sourceKey)) {
                throw new IllegalArgumentException("Reviewed named-brand imports require the exact canonical source key; alias migration needs review.");
            }
            if (hasBarcode || declared != null && declared != FoodCatalogType.BRANDED_PRODUCT) {
                throw new IllegalArgumentException("Named USDA manufacturer is not generic or an independently verified retail GTIN.");
            }
            return FoodCatalogType.BRANDED_PRODUCT;
        }
        return resolve(dataType, declared, hasBarcode);
    }

    public static void validateSourceIdentity(String fdcId, String sourceKey) {
        if (sourceKey != null && sourceKey.toUpperCase(Locale.ROOT).startsWith("USDA")
                && !sourceKey.matches("USDA(?:_FOODDATA)?:fdc:[1-9][0-9]{0,17}")) {
            throw new IllegalArgumentException("Malformed USDA provider source key.");
        }
        if (fdcId == null) {
            return;
        }
        if (!fdcId.matches("[1-9][0-9]{0,17}")) {
            throw new IllegalArgumentException("USDA fdc_id must be a positive numeric source id.");
        }
        if (sourceKey != null && !sourceKey.equals("USDA_FOODDATA:fdc:" + fdcId)
                && !sourceKey.equals("USDA:fdc:" + fdcId)) {
            throw new IllegalArgumentException("USDA fdc_id and source_key must identify the same source record.");
        }
    }
}
