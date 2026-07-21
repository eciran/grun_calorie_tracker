package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodSearchTelemetrySummaryDto;

public interface FoodSearchTelemetryAdminService {
    FoodSearchTelemetrySummaryDto getSummary(int hours);
}