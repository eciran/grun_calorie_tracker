package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.RevenueCatMonitoringChartsDto;
import com.grun.calorietracker.dto.RevenueCatMonitoringOverviewDto;

public interface RevenueCatMonitoringService {
    RevenueCatMonitoringOverviewDto getOverview(String environment);

    RevenueCatMonitoringChartsDto getCharts(String environment, String range, String startDate, String endDate);
}
