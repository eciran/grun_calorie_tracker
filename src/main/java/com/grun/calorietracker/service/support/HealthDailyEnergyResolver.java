package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.DeviceDataEntity;
import com.grun.calorietracker.enums.EnergyExpenditureSource;
import com.grun.calorietracker.enums.HealthProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class HealthDailyEnergyResolver {

    public List<HealthDailyEnergySnapshot> resolve(
            List<DeviceDataEntity> metrics,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (metrics == null || metrics.isEmpty()) {
            return List.of();
        }

        Map<String, DeviceDataEntity> deduplicated = new LinkedHashMap<>();
        for (DeviceDataEntity metric : metrics) {
            if (metric == null
                    || metric.getProvider() == null
                    || metric.getProvider() == HealthProvider.MANUAL
                    || metric.getRecordedAt() == null) {
                continue;
            }
            LocalDate date = metric.getRecordedAt().toLocalDate();
            if (date.isBefore(startDate) || date.isAfter(endDate)) {
                continue;
            }
            deduplicated.merge(eventKey(metric), metric, this::latest);
        }

        Map<LocalDate, Map<HealthProvider, List<DeviceDataEntity>>> grouped = new LinkedHashMap<>();
        deduplicated.values().forEach(metric -> grouped
                .computeIfAbsent(metric.getRecordedAt().toLocalDate(), ignored -> new LinkedHashMap<>())
                .computeIfAbsent(metric.getProvider(), ignored -> new ArrayList<>())
                .add(metric));

        return grouped.entrySet().stream()
                .map(entry -> entry.getValue().entrySet().stream()
                        .map(providerEntry -> aggregate(entry.getKey(), providerEntry.getKey(), providerEntry.getValue()))
                        .max(candidateComparator())
                        .orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(HealthDailyEnergySnapshot::date))
                .toList();
    }

    private HealthDailyEnergySnapshot aggregate(
            LocalDate date,
            HealthProvider provider,
            List<DeviceDataEntity> metrics
    ) {
        Double active = sumNullable(metrics.stream()
                .map(metric -> metric.getActiveEnergyCalories() != null
                        ? metric.getActiveEnergyCalories()
                        : metric.getCaloriesBurned())
                .toList());
        Double resting = sumNullable(metrics.stream().map(DeviceDataEntity::getRestingEnergyCalories).toList());
        Double directTotal = sumNullable(metrics.stream().map(DeviceDataEntity::getTotalEnergyCalories).toList());

        if (directTotal != null) {
            return new HealthDailyEnergySnapshot(
                    date, provider, round(active), round(resting), round(directTotal),
                    EnergyExpenditureSource.HEALTH_TOTAL_ENERGY);
        }
        if (active != null && resting != null) {
            return new HealthDailyEnergySnapshot(
                    date, provider, round(active), round(resting), round(active + resting),
                    EnergyExpenditureSource.HEALTH_RESTING_PLUS_ACTIVE);
        }
        return new HealthDailyEnergySnapshot(
                date, provider, round(active), round(resting), null, EnergyExpenditureSource.UNAVAILABLE);
    }

    private Comparator<HealthDailyEnergySnapshot> candidateComparator() {
        return Comparator
                .comparingInt(this::qualityRank)
                .thenComparing(snapshot -> snapshot.provider().name(), Comparator.reverseOrder());
    }

    private int qualityRank(HealthDailyEnergySnapshot snapshot) {
        return switch (snapshot.expenditureSource()) {
            case HEALTH_TOTAL_ENERGY -> 3;
            case HEALTH_RESTING_PLUS_ACTIVE -> 2;
            default -> snapshot.activeEnergyCalories() != null || snapshot.restingEnergyCalories() != null ? 1 : 0;
        };
    }

    private DeviceDataEntity latest(DeviceDataEntity first, DeviceDataEntity second) {
        LocalDateTime firstTime = first.getRecordedAt();
        LocalDateTime secondTime = second.getRecordedAt();
        return secondTime.isAfter(firstTime) ? second : first;
    }

    private String eventKey(DeviceDataEntity metric) {
        String identity;
        if (metric.getExternalId() != null && !metric.getExternalId().isBlank()) {
            identity = "external:" + metric.getExternalId().trim();
        } else if (metric.getId() != null) {
            identity = "id:" + metric.getId();
        } else {
            identity = "sample:" + metric.getRecordedAt() + ":" + Objects.hash(
                    metric.getActiveEnergyCalories(),
                    metric.getRestingEnergyCalories(),
                    metric.getTotalEnergyCalories(),
                    metric.getCaloriesBurned()
            );
        }
        return metric.getProvider().name() + ":" + identity;
    }

    private Double sumNullable(List<Double> values) {
        List<Double> present = values.stream().filter(Objects::nonNull).toList();
        return present.isEmpty() ? null : present.stream().mapToDouble(Double::doubleValue).sum();
    }

    private Double round(Double value) {
        return value == null ? null : Math.round(value * 100.0) / 100.0;
    }
}