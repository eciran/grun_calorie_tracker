package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodSearchTelemetrySummaryDto;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.repository.FoodSearchTelemetryRepository;
import com.grun.calorietracker.service.FoodSearchTelemetryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
    public FoodSearchTelemetrySummaryDto getSummary(int hours, MarketRegion region,
                                                    PreferredLanguage language) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        Instant noSelectionCutoff = Instant.now().minus(30, ChronoUnit.MINUTES);
        long searches = telemetryRepository.countFiltered(since, region, language);
        long zeroResults = telemetryRepository.countZeroResultFiltered(since, region, language);
        long selected = telemetryRepository.countSelectedFiltered(since, region, language);
        long noSelection = telemetryRepository.countNoSelectionFiltered(
                since, noSelectionCutoff, region, language);
        var topQueries = telemetryRepository.topZeroResultQueries(
                        since, region, language, PageRequest.of(0, 10)).stream()
                .map(item -> new FoodSearchTelemetrySummaryDto.ZeroResultQuery(
                        item.getQuery(), item.getSearches()))
                .toList();
        return new FoodSearchTelemetrySummaryDto(hours, searches, zeroResults, selected, noSelection,
                rate(zeroResults, searches), rate(selected, searches), topQueries);
    }

    private double rate(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : Math.round((numerator * 10000.0 / denominator)) / 10000.0;
    }
}