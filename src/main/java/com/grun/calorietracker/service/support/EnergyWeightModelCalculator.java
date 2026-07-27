package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.EnergyBalanceAnalyticsDto;
import com.grun.calorietracker.entity.BodyMeasurementEntity;
import com.grun.calorietracker.enums.EnergyWeightModelStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EnergyWeightModelCalculator {

    private static final double MIN_WEIGHT_KG = 20.0;
    private static final double MAX_WEIGHT_KG = 500.0;

    private final EnergyBalancePolicy policy;

    public EnergyBalanceAnalyticsDto.WeightModel calculate(
            LocalDate startDate,
            LocalDate endDate,
            Double cumulativeEnergyBalanceCalories,
            int evaluatedDays,
            List<BodyMeasurementEntity> measurements
    ) {
        EnergyBalanceAnalyticsDto.WeightModel result = baseResult();
        long rangeDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (rangeDays < policy.minimumWeightModelDays()) {
            result.setStatus(EnergyWeightModelStatus.INSUFFICIENT_RANGE);
            return result;
        }
        if (cumulativeEnergyBalanceCalories == null || evaluatedDays < policy.minimumWeightModelDays()) {
            result.setStatus(EnergyWeightModelStatus.MISSING_ENERGY_DATA);
            return result;
        }

        double modeled = round(cumulativeEnergyBalanceCalories / policy.energyPerKgCoefficient());
        double margin = Math.abs(modeled) * policy.weightModelUncertaintyPercent();
        result.setModeledWeightChangeKg(modeled);
        result.setModeledWeightChangeLowerKg(round(modeled - margin));
        result.setModeledWeightChangeUpperKg(round(modeled + margin));

        List<BodyMeasurementEntity> dailyWeights = normalizeMeasurements(measurements, startDate, endDate);
        if (dailyWeights.size() < 2) {
            result.setStatus(EnergyWeightModelStatus.MISSING_WEIGHT_BASELINE);
            return result;
        }

        BodyMeasurementEntity first = dailyWeights.get(0);
        BodyMeasurementEntity last = dailyWeights.get(dailyWeights.size() - 1);
        double observed = round(last.getWeightKg() - first.getWeightKg());
        result.setStartWeightKg(round(first.getWeightKg()));
        result.setStartWeightDate(first.getRecordedAt().toLocalDate());
        result.setEndWeightKg(round(last.getWeightKg()));
        result.setEndWeightDate(last.getRecordedAt().toLocalDate());
        result.setObservedWeightChangeKg(observed);
        result.setDifferenceFromModelKg(round(observed - modeled));
        result.setStatus(EnergyWeightModelStatus.AVAILABLE);
        return result;
    }

    private EnergyBalanceAnalyticsDto.WeightModel baseResult() {
        return EnergyBalanceAnalyticsDto.WeightModel.builder()
                .modelCode(policy.weightModelCode())
                .energyPerKgCoefficient(policy.energyPerKgCoefficient())
                .status(EnergyWeightModelStatus.INSUFFICIENT_DATA)
                .build();
    }

    private List<BodyMeasurementEntity> normalizeMeasurements(
            List<BodyMeasurementEntity> measurements,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (measurements == null || measurements.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, BodyMeasurementEntity> latestByDay = new LinkedHashMap<>();
        measurements.stream()
                .filter(this::isValidWeight)
                .filter(measurement -> {
                    LocalDate date = measurement.getRecordedAt().toLocalDate();
                    return !date.isBefore(startDate) && !date.isAfter(endDate);
                })
                .sorted(Comparator.comparing(BodyMeasurementEntity::getRecordedAt))
                .forEach(measurement -> latestByDay.put(measurement.getRecordedAt().toLocalDate(), measurement));
        return latestByDay.values().stream()
                .sorted(Comparator.comparing(BodyMeasurementEntity::getRecordedAt))
                .toList();
    }

    private boolean isValidWeight(BodyMeasurementEntity measurement) {
        return measurement != null
                && measurement.getRecordedAt() != null
                && measurement.getWeightKg() != null
                && Double.isFinite(measurement.getWeightKg())
                && measurement.getWeightKg() >= MIN_WEIGHT_KG
                && measurement.getWeightKg() <= MAX_WEIGHT_KG;
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}