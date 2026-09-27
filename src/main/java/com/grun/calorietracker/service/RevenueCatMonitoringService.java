package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.RevenueCatMonitoringChartsDto;
import com.grun.calorietracker.dto.RevenueCatMonitoringOverviewDto;
import com.grun.calorietracker.dto.RevenueCatCustomerEvidenceDto;

public interface RevenueCatMonitoringService {
    RevenueCatMonitoringOverviewDto getOverview(String environment);

    RevenueCatMonitoringChartsDto getCharts(String environment, String range, String startDate, String endDate);

    RevenueCatCustomerEvidenceDto getCustomerEvidence(Long userId);
}
