package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodSearchTelemetrySummaryDto;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;

public interface FoodSearchTelemetryAdminService {
    default FoodSearchTelemetrySummaryDto getSummary(int hours) {
        return getSummary(hours, null, null);
    }

    FoodSearchTelemetrySummaryDto getSummary(int hours, MarketRegion region,
                                             PreferredLanguage language);
}