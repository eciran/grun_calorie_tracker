package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodSearchTelemetrySummaryDto;
import com.grun.calorietracker.repository.FoodSearchTelemetryRepository;
import com.grun.calorietracker.service.FoodSearchTelemetryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class FoodSearchTelemetryAdminServiceImpl implements FoodSearchTelemetryAdminService {
    private final FoodSearchTelemetryRepository telemetryRepository;

    @Override
    @Transactional(readOnly = true)
    public FoodSearchTelemetrySummaryDto getSummary(int hours) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        Instant noSelectionCutoff = Instant.now().minus(30, ChronoUnit.MINUTES);
        long searches = telemetryRepository.countBySearchedAtAfter(since);
        long zeroResults = telemetryRepository.countBySearchedAtAfterAndResultCount(since, 0);
        long selected = telemetryRepository.countBySearchedAtAfterAndSelectedAtIsNotNull(since);
        long noSelection = telemetryRepository.countNoSelection(since, noSelectionCutoff);
        return new FoodSearchTelemetrySummaryDto(hours, searches, zeroResults, selected, noSelection,
                rate(zeroResults, searches), rate(selected, searches));
    }

    private double rate(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : Math.round((numerator * 10000.0 / denominator)) / 10000.0;
    }
}