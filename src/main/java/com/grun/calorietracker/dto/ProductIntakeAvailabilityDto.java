package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.MarketRegion;

import java.util.List;

public record ProductIntakeAvailabilityDto(
        boolean available,
        String reason,
        MarketRegion marketRegion,
        List<String> fallbacks
) {
    public static final List<String> EXISTING_FALLBACKS =
            List.of("SEARCH_MANUALLY", "CREATE_CUSTOM_FOOD");
}