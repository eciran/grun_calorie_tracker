package com.grun.calorietracker.dto;

public record FoodBrandSummaryDto(
        Long id,
        String canonicalName,
        String manufacturerName,
        String countryCode,
        String logoUrl,
        boolean verified
) { }
