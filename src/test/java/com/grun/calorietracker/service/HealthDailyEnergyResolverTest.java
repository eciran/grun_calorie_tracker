package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.DeviceDataEntity;
import com.grun.calorietracker.enums.EnergyExpenditureSource;
import com.grun.calorietracker.enums.HealthProvider;
import com.grun.calorietracker.service.support.HealthDailyEnergyResolver;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HealthDailyEnergyResolverTest {

    private final HealthDailyEnergyResolver resolver = new HealthDailyEnergyResolver();

    @Test
    void resolve_prefersDirectTotalWithoutCombiningProviders() {
        DeviceDataEntity apple = metric(HealthProvider.APPLE_HEALTH, "apple-total", 500.0, 1600.0, 2100.0, 8);
        DeviceDataEntity healthConnect = metric(HealthProvider.HEALTH_CONNECT, "hc-components", 700.0, 1700.0, null, 9);

        var result = resolver.resolve(List.of(apple, healthConnect), date(), date());

        assertEquals(1, result.size());
        assertEquals(HealthProvider.APPLE_HEALTH, result.get(0).provider());
        assertEquals(2100.0, result.get(0).totalEnergyCalories());
        assertEquals(EnergyExpenditureSource.HEALTH_TOTAL_ENERGY, result.get(0).expenditureSource());
    }

    @Test
    void resolve_combinesRestingAndActiveWithinOneProvider() {
        DeviceDataEntity active = metric(HealthProvider.HEALTH_CONNECT, "active", 420.0, null, null, 8);
        DeviceDataEntity resting = metric(HealthProvider.HEALTH_CONNECT, "resting", null, 1650.0, null, 9);

        var result = resolver.resolve(List.of(active, resting), date(), date());

        assertEquals(2070.0, result.get(0).totalEnergyCalories());
        assertEquals(EnergyExpenditureSource.HEALTH_RESTING_PLUS_ACTIVE, result.get(0).expenditureSource());
    }

    @Test
    void resolve_deduplicatesSameProviderExternalEvent() {
        DeviceDataEntity first = metric(HealthProvider.APPLE_HEALTH, "same-event", 200.0, null, null, 8);
        DeviceDataEntity updated = metric(HealthProvider.APPLE_HEALTH, "same-event", 250.0, null, null, 9);

        var result = resolver.resolve(List.of(first, updated), date(), date());

        assertEquals(250.0, result.get(0).activeEnergyCalories());
        assertNull(result.get(0).totalEnergyCalories());
    }

    @Test
    void resolve_usesLegacyCaloriesAsActiveEnergy() {
        DeviceDataEntity legacy = metric(HealthProvider.GOOGLE_FIT, "legacy", null, null, null, 8);
        legacy.setCaloriesBurned(315.0);

        var result = resolver.resolve(List.of(legacy), date(), date());

        assertEquals(315.0, result.get(0).activeEnergyCalories());
        assertEquals(EnergyExpenditureSource.UNAVAILABLE, result.get(0).expenditureSource());
    }

    private DeviceDataEntity metric(
            HealthProvider provider,
            String externalId,
            Double active,
            Double resting,
            Double total,
            int hour
    ) {
        DeviceDataEntity metric = new DeviceDataEntity();
        metric.setProvider(provider);
        metric.setExternalId(externalId);
        metric.setRecordedAt(LocalDateTime.of(2026, 7, 27, hour, 0));
        metric.setActiveEnergyCalories(active);
        metric.setRestingEnergyCalories(resting);
        metric.setTotalEnergyCalories(total);
        return metric;
    }

    private LocalDate date() {
        return LocalDate.of(2026, 7, 27);
    }
}