package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminTrackingSummaryDto;

public interface AdminTrackingMonitoringService {
    AdminTrackingSummaryDto getSummary(Integer days);
}
