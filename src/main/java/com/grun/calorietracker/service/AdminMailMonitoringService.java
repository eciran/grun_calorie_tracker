package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminMailMonitoringDto;

public interface AdminMailMonitoringService {
    AdminMailMonitoringDto getMonitoring(int days, int limit);
}
