package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.MarketRegion;

public record ProductIntakeAvailabilityDto(
        boolean available,
        String reason,
        MarketRegion marketRegion
) {
}