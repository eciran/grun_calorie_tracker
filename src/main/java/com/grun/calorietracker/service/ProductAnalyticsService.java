package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.ProductAnalyticsEventDto;
import com.grun.calorietracker.dto.ProductAnalyticsEventRequestDto;

public interface ProductAnalyticsService {
    ProductAnalyticsEventDto recordEvent(String email, ProductAnalyticsEventRequestDto request);
}
