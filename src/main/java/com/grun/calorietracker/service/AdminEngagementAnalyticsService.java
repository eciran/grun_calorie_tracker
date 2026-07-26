package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminEngagementAnalyticsDto;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.SubscriptionPlan;

public interface AdminEngagementAnalyticsService {
    AdminEngagementAnalyticsDto getSummary(int hours, MarketRegion region,
                                           PreferredLanguage language,
                                           SubscriptionPlan plan);
}
