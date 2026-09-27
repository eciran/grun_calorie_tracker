package com.grun.calorietracker.dto;

public record CatalogClassificationQualityDto(
        long totalProducts,
        long productsWithLegacyBrand,
        long productsWithCanonicalBrand,
        long productsWithPrimaryCategory,
        long brandedProductsMissingLegacyBrand,
        long unresolvedLegacyBrands
) { }
