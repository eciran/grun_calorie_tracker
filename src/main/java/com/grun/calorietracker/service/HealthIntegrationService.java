package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.HealthConnectionDto;
import com.grun.calorietracker.dto.HealthConnectionRequestDto;
import com.grun.calorietracker.dto.HealthDataDeleteResponseDto;
import com.grun.calorietracker.dto.HealthDailySummaryDto;
import com.grun.calorietracker.dto.HealthMetricBatchSyncRequestDto;
import com.grun.calorietracker.dto.HealthMetricBatchSyncResponseDto;
import com.grun.calorietracker.dto.HealthMetricSyncRequestDto;
import com.grun.calorietracker.dto.HealthMetricSyncResponseDto;
import com.grun.calorietracker.dto.HealthRangeSummaryDto;
import com.grun.calorietracker.enums.HealthProvider;

import java.time.LocalDate;
import java.util.List;

public interface HealthIntegrationService {
    List<HealthConnectionDto> getConnections(String email);
    HealthDailySummaryDto getDailySummary(String email, LocalDate date);
    HealthRangeSummaryDto getRangeSummary(String email, LocalDate startDate, LocalDate endDate);
    HealthConnectionDto connect(String email, HealthProvider provider, HealthConnectionRequestDto request);
    HealthConnectionDto disconnect(String email, HealthProvider provider);
    HealthMetricSyncResponseDto syncMetric(String email, HealthProvider provider, HealthMetricSyncRequestDto request);
    HealthMetricBatchSyncResponseDto syncMetrics(String email, HealthProvider provider, HealthMetricBatchSyncRequestDto request);
    HealthDataDeleteResponseDto deleteProviderData(String email, HealthProvider provider);
    HealthDataDeleteResponseDto deleteAllHealthData(String email);
}
