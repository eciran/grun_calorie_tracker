package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.EnergyBalanceAnalyticsDto;
import com.grun.calorietracker.entity.ExerciseLogsEntity;
import com.grun.calorietracker.entity.FoodLogsEntity;
import com.grun.calorietracker.enums.AnalyticsInsightTone;
import com.grun.calorietracker.enums.EnergyBalanceInsightCode;
import com.grun.calorietracker.enums.EnergyBalanceState;
import com.grun.calorietracker.enums.EnergyDataConfidence;
import com.grun.calorietracker.enums.EnergyExpenditureSource;
import com.grun.calorietracker.enums.EnergyWeightModelStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EnergyBalanceAnalyticsAssembler {

    private static final Set<String> CANONICAL_MEALS = Set.of("BREAKFAST", "LUNCH", "DINNER", "SNACK");

    private final EnergyBalancePolicy policy;

    public EnergyBalanceAnalyticsDto assemble(
            LocalDate startDate,
            LocalDate endDate,
            String timeZone,
            List<DailyCalorieIntakeSnapshot> intakeDays,
            List<DailyEnergyExpenditureSnapshot> expenditureDays,
            List<FoodLogsEntity> foodLogs,
            List<ExerciseLogsEntity> exerciseLogs,
            EnergyBalanceAnalyticsDto.WeightModel weightModel
    ) {
        Map<LocalDate, DailyCalorieIntakeSnapshot> intakeByDate = indexIntake(intakeDays);
        Map<LocalDate, DailyEnergyExpenditureSnapshot> expenditureByDate = indexExpenditure(expenditureDays);
        List<EnergyBalanceAnalyticsDto.DailyPoint> points = new ArrayList<>();
        double cumulative = 0.0;
        int evaluatedDays = 0;

        for (LocalDate date : startDate.datesUntil(endDate.plusDays(1)).toList()) {
            DailyCalorieIntakeSnapshot intake = intakeByDate.get(date);
            DailyEnergyExpenditureSnapshot expenditure = expenditureByDate.get(date);
            Double consumed = intake != null && intake.foodLogged() ? intake.consumedCalories() : null;
            Double totalExpenditure = expenditure == null ? null : expenditure.totalExpenditureCalories();
            Double balance = consumed != null && totalExpenditure != null ? round(consumed - totalExpenditure) : null;
            EnergyBalanceState state = policy.resolveBalanceState(consumed, totalExpenditure);
            Double cumulativePoint = null;
            if (balance != null) {
                cumulative += balance;
                cumulativePoint = round(cumulative);
                evaluatedDays++;
            }
            points.add(EnergyBalanceAnalyticsDto.DailyPoint.builder()
                    .date(date)
                    .consumedCalories(round(consumed))
                    .restingEnergyCalories(expenditure == null ? null : expenditure.restingEnergyCalories())
                    .activeEnergyCalories(expenditure == null ? null : expenditure.activeEnergyCalories())
                    .totalExpenditureCalories(round(totalExpenditure))
                    .energyBalanceCalories(balance)
                    .cumulativeBalanceCalories(cumulativePoint)
                    .balanceState(state)
                    .expenditureSource(expenditure == null ? EnergyExpenditureSource.UNAVAILABLE : expenditure.source())
                    .foodLogged(intake != null && intake.foodLogged())
                    .expenditureAvailable(totalExpenditure != null)
                    .build());
        }

        int rangeDays = points.size();
        EnergyBalanceAnalyticsDto.Coverage coverage = buildCoverage(points, rangeDays, evaluatedDays);
        EnergyBalanceAnalyticsDto.Summary summary = buildSummary(points, evaluatedDays);
        return EnergyBalanceAnalyticsDto.builder()
                .range(EnergyBalanceAnalyticsDto.Range.builder()
                        .startDate(startDate)
                        .endDate(endDate)
                        .dayCount(rangeDays)
                        .aggregation("DAY")
                        .timeZone(timeZone)
                        .build())
                .summary(summary)
                .weightModel(weightModel)
                .averages(buildAverages(points))
                .coverage(coverage)
                .dailyPoints(points)
                .breakdown(EnergyBalanceAnalyticsDto.Breakdown.builder()
                        .meals(buildMealBreakdown(foodLogs, startDate, endDate))
                        .activities(buildActivityBreakdown(exerciseLogs, startDate, endDate))
                        .build())
                .insights(buildInsights(summary, coverage, weightModel))
                .build();
    }

    private EnergyBalanceAnalyticsDto.Summary buildSummary(
            List<EnergyBalanceAnalyticsDto.DailyPoint> points,
            int evaluatedDays
    ) {
        List<EnergyBalanceAnalyticsDto.DailyPoint> evaluated = points.stream()
                .filter(point -> point.getEnergyBalanceCalories() != null)
                .toList();
        Double pairedConsumed = sumNullable(evaluated.stream().map(EnergyBalanceAnalyticsDto.DailyPoint::getConsumedCalories).toList());
        Double pairedExpenditure = sumNullable(evaluated.stream().map(EnergyBalanceAnalyticsDto.DailyPoint::getTotalExpenditureCalories).toList());
        Double cumulative = sumNullable(evaluated.stream().map(EnergyBalanceAnalyticsDto.DailyPoint::getEnergyBalanceCalories).toList());
        return EnergyBalanceAnalyticsDto.Summary.builder()
                .balanceState(policy.resolveBalanceState(pairedConsumed, pairedExpenditure))
                .totalConsumedCalories(sumNullable(points.stream().map(EnergyBalanceAnalyticsDto.DailyPoint::getConsumedCalories).toList()))
                .totalRestingEnergyCalories(sumNullable(points.stream().map(EnergyBalanceAnalyticsDto.DailyPoint::getRestingEnergyCalories).toList()))
                .totalActiveEnergyCalories(sumNullable(points.stream().map(EnergyBalanceAnalyticsDto.DailyPoint::getActiveEnergyCalories).toList()))
                .totalExpenditureCalories(sumNullable(points.stream().map(EnergyBalanceAnalyticsDto.DailyPoint::getTotalExpenditureCalories).toList()))
                .cumulativeBalanceCalories(round(cumulative))
                .averageDailyBalanceCalories(evaluatedDays == 0 ? null : round(cumulative / evaluatedDays))
                .deficitDays((int) evaluated.stream().filter(point -> point.getBalanceState() == EnergyBalanceState.DEFICIT).count())
                .surplusDays((int) evaluated.stream().filter(point -> point.getBalanceState() == EnergyBalanceState.SURPLUS).count())
                .balancedDays((int) evaluated.stream().filter(point -> point.getBalanceState() == EnergyBalanceState.BALANCED).count())
                .evaluatedDays(evaluatedDays)
                .build();
    }

    private EnergyBalanceAnalyticsDto.Coverage buildCoverage(
            List<EnergyBalanceAnalyticsDto.DailyPoint> points,
            int rangeDays,
            int evaluatedDays
    ) {
        int foodDays = (int) points.stream().filter(point -> Boolean.TRUE.equals(point.getFoodLogged())).count();
        int restingDays = (int) points.stream().filter(point -> point.getRestingEnergyCalories() != null).count();
        int activeDays = (int) points.stream().filter(point -> point.getActiveEnergyCalories() != null).count();
        int expenditureDays = (int) points.stream().filter(point -> Boolean.TRUE.equals(point.getExpenditureAvailable())).count();
        int healthDays = (int) points.stream().filter(point -> isHealthSource(point.getExpenditureSource())).count();
        int profileDays = (int) points.stream()
                .filter(point -> point.getExpenditureSource() == EnergyExpenditureSource.PROFILE_TDEE_ESTIMATE
                        || point.getExpenditureSource() == EnergyExpenditureSource.PROFILE_TDEE_PLUS_LOGGED_ACTIVITY)
                .count();
        return EnergyBalanceAnalyticsDto.Coverage.builder()
                .foodLoggedDays(foodDays)
                .restingEnergyAvailableDays(restingDays)
                .activeEnergyAvailableDays(activeDays)
                .expenditureAvailableDays(expenditureDays)
                .fullyEvaluatedDays(evaluatedDays)
                .rangeDayCount(rangeDays)
                .foodCoveragePercent(percent(foodDays, rangeDays))
                .expenditureCoveragePercent(percent(expenditureDays, rangeDays))
                .evaluatedCoveragePercent(percent(evaluatedDays, rangeDays))
                .healthProviderDays(healthDays)
                .profileEstimateDays(profileDays)
                .dataConfidence(policy.resolveConfidence(rangeDays, evaluatedDays, expenditureDays, healthDays))
                .build();
    }

    private EnergyBalanceAnalyticsDto.Averages buildAverages(List<EnergyBalanceAnalyticsDto.DailyPoint> points) {
        return EnergyBalanceAnalyticsDto.Averages.builder()
                .consumedCaloriesOnLoggedDays(average(points.stream().map(EnergyBalanceAnalyticsDto.DailyPoint::getConsumedCalories).toList()))
                .restingEnergyCaloriesOnAvailableDays(average(points.stream().map(EnergyBalanceAnalyticsDto.DailyPoint::getRestingEnergyCalories).toList()))
                .activeEnergyCaloriesOnAvailableDays(average(points.stream().map(EnergyBalanceAnalyticsDto.DailyPoint::getActiveEnergyCalories).toList()))
                .expenditureCaloriesOnAvailableDays(average(points.stream().map(EnergyBalanceAnalyticsDto.DailyPoint::getTotalExpenditureCalories).toList()))
                .build();
    }

    private List<EnergyBalanceAnalyticsDto.MealBreakdown> buildMealBreakdown(
            List<FoodLogsEntity> logs,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Map<String, Double> caloriesByMeal = new LinkedHashMap<>();
        safe(logs).stream()
                .filter(log -> log != null
                        && isWithinRange(log.getLogDate(), startDate, endDate)
                        && canonicalMeal(log.getMealType()) != null)
                .forEach(log -> caloriesByMeal.merge(canonicalMeal(log.getMealType()), foodCalories(log), Double::sum));
        double total = caloriesByMeal.values().stream().mapToDouble(Double::doubleValue).sum();
        return caloriesByMeal.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> EnergyBalanceAnalyticsDto.MealBreakdown.builder()
                        .mealType(entry.getKey())
                        .totalCalories(round(entry.getValue()))
                        .sharePercent(total <= 0.0 ? 0.0 : round(entry.getValue() * 100.0 / total))
                        .build())
                .toList();
    }

    private List<EnergyBalanceAnalyticsDto.ActivityBreakdown> buildActivityBreakdown(
            List<ExerciseLogsEntity> logs,
            LocalDate startDate,
            LocalDate endDate
    ) {
        record ActivityKey(String category, String source) { }
        Map<ActivityKey, List<ExerciseLogsEntity>> grouped = safe(logs).stream()
                .filter(log -> log != null && isWithinRange(log.getLogDate(), startDate, endDate))
                .collect(Collectors.groupingBy(
                        log -> new ActivityKey(activityCategory(log), normalizedSource(log.getSource())),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        return grouped.entrySet().stream()
                .map(entry -> EnergyBalanceAnalyticsDto.ActivityBreakdown.builder()
                        .category(entry.getKey().category())
                        .source(entry.getKey().source())
                        .totalCalories(round(sumNullable(entry.getValue().stream().map(ExerciseLogsEntity::getCaloriesBurned).toList())))
                        .durationMinutes(sumIntegers(entry.getValue().stream().map(ExerciseLogsEntity::getDurationMinutes).toList()))
                        .includedInExpenditure(isManualSource(entry.getKey().source()))
                        .build())
                .sorted(Comparator.comparing(EnergyBalanceAnalyticsDto.ActivityBreakdown::getCategory)
                        .thenComparing(EnergyBalanceAnalyticsDto.ActivityBreakdown::getSource))
                .toList();
    }

    private List<EnergyBalanceAnalyticsDto.Insight> buildInsights(
            EnergyBalanceAnalyticsDto.Summary summary,
            EnergyBalanceAnalyticsDto.Coverage coverage,
            EnergyBalanceAnalyticsDto.WeightModel weightModel
    ) {
        List<EnergyBalanceAnalyticsDto.Insight> insights = new ArrayList<>();
        if (coverage.getDataConfidence() == EnergyDataConfidence.LOW
                || coverage.getDataConfidence() == EnergyDataConfidence.INSUFFICIENT) {
            insights.add(insight(EnergyBalanceInsightCode.ENERGY_DATA_INCOMPLETE, AnalyticsInsightTone.CAUTION,
                    10, coverage.getEvaluatedCoveragePercent(), "PERCENT"));
        }
        switch (summary.getBalanceState()) {
            case DEFICIT -> insights.add(insight(EnergyBalanceInsightCode.ENERGY_BALANCE_DEFICIT,
                    AnalyticsInsightTone.NEUTRAL, 20, summary.getAverageDailyBalanceCalories(), "KCAL_PER_DAY"));
            case SURPLUS -> insights.add(insight(EnergyBalanceInsightCode.ENERGY_BALANCE_SURPLUS,
                    AnalyticsInsightTone.NEUTRAL, 20, summary.getAverageDailyBalanceCalories(), "KCAL_PER_DAY"));
            case BALANCED -> insights.add(insight(EnergyBalanceInsightCode.ENERGY_BALANCE_STABLE,
                    AnalyticsInsightTone.NEUTRAL, 20, summary.getAverageDailyBalanceCalories(), "KCAL_PER_DAY"));
            default -> { }
        }
        if (weightModel != null && weightModel.getModeledWeightChangeKg() != null) {
            insights.add(insight(EnergyBalanceInsightCode.MODELED_WEIGHT_CHANGE_AVAILABLE,
                    AnalyticsInsightTone.NEUTRAL, 30, weightModel.getModeledWeightChangeKg(), "KG"));
        }
        if (weightModel == null || weightModel.getStatus() != EnergyWeightModelStatus.AVAILABLE) {
            insights.add(insight(EnergyBalanceInsightCode.WEIGHT_DATA_INCOMPLETE,
                    AnalyticsInsightTone.NEUTRAL, 40, null, null));
        } else if (withinModeledRange(weightModel)) {
            insights.add(insight(EnergyBalanceInsightCode.OBSERVED_CHANGE_CLOSE_TO_MODEL,
                    AnalyticsInsightTone.NEUTRAL, 40, weightModel.getDifferenceFromModelKg(), "KG"));
        } else {
            insights.add(insight(EnergyBalanceInsightCode.OBSERVED_CHANGE_DIFFERS_FROM_MODEL,
                    AnalyticsInsightTone.NEUTRAL, 40, weightModel.getDifferenceFromModelKg(), "KG"));
        }
        return insights.stream().sorted(Comparator.comparing(EnergyBalanceAnalyticsDto.Insight::getPriority)).toList();
    }

    private boolean withinModeledRange(EnergyBalanceAnalyticsDto.WeightModel model) {
        return model.getObservedWeightChangeKg() != null
                && model.getModeledWeightChangeLowerKg() != null
                && model.getModeledWeightChangeUpperKg() != null
                && model.getObservedWeightChangeKg() >= model.getModeledWeightChangeLowerKg()
                && model.getObservedWeightChangeKg() <= model.getModeledWeightChangeUpperKg();
    }

    private EnergyBalanceAnalyticsDto.Insight insight(
            EnergyBalanceInsightCode code, AnalyticsInsightTone tone, int priority, Double value, String unit) {
        return EnergyBalanceAnalyticsDto.Insight.builder()
                .code(code).tone(tone).priority(priority).value(round(value)).unit(unit).build();
    }

    private Map<LocalDate, DailyCalorieIntakeSnapshot> indexIntake(List<DailyCalorieIntakeSnapshot> values) {
        return safe(values).stream().filter(Objects::nonNull).collect(Collectors.toMap(
                DailyCalorieIntakeSnapshot::date, Function.identity(), (first, second) -> second));
    }

    private Map<LocalDate, DailyEnergyExpenditureSnapshot> indexExpenditure(List<DailyEnergyExpenditureSnapshot> values) {
        return safe(values).stream().filter(Objects::nonNull).collect(Collectors.toMap(
                DailyEnergyExpenditureSnapshot::date, Function.identity(), (first, second) -> second));
    }

    private boolean isHealthSource(EnergyExpenditureSource source) {
        return source == EnergyExpenditureSource.HEALTH_TOTAL_ENERGY
                || source == EnergyExpenditureSource.HEALTH_RESTING_PLUS_ACTIVE
                || source == EnergyExpenditureSource.HEALTH_ACTIVE_PLUS_PROFILE_RESTING
                || source == EnergyExpenditureSource.HEALTH_TOTAL_PLUS_LOGGED_ACTIVITY
                || source == EnergyExpenditureSource.HEALTH_ACTIVE_PLUS_PROFILE_RESTING_AND_LOGGED_ACTIVITY;
    }

    private String canonicalMeal(String value) {
        String normalized = value == null ? null : value.trim().toUpperCase();
        return normalized != null && CANONICAL_MEALS.contains(normalized) ? normalized : null;
    }

    private double foodCalories(FoodLogsEntity log) {
        if (log.getSnapshotCalories() != null && Double.isFinite(log.getSnapshotCalories())) {
            return Math.max(0.0, log.getSnapshotCalories());
        }
        if (log.getFoodItem() == null || log.getFoodItem().getCalories() == null) {
            return 0.0;
        }
        Double grams = log.getNormalizedPortionGrams() != null ? log.getNormalizedPortionGrams() : log.getPortionSize();
        return grams == null ? 0.0 : Math.max(0.0, log.getFoodItem().getCalories() * grams / 100.0);
    }

    private String activityCategory(ExerciseLogsEntity log) {
        if (log.getExerciseItem() == null || log.getExerciseItem().getName() == null
                || log.getExerciseItem().getName().isBlank()) {
            return "UNMAPPED";
        }
        return log.getExerciseItem().getName().trim();
    }

    private boolean isWithinRange(java.time.LocalDateTime value, LocalDate startDate, LocalDate endDate) {
        if (value == null) {
            return false;
        }
        LocalDate date = value.toLocalDate();
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    private String normalizedSource(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.trim().toUpperCase();
    }

    private boolean isManualSource(String source) {
        return "MANUAL".equalsIgnoreCase(source);
    }

    private Integer sumIntegers(List<Integer> values) {
        List<Integer> present = values.stream().filter(Objects::nonNull).toList();
        return present.isEmpty() ? null : present.stream().mapToInt(Integer::intValue).sum();
    }

    private Double sumNullable(List<Double> values) {
        List<Double> present = values.stream().filter(Objects::nonNull).filter(Double::isFinite).toList();
        return present.isEmpty() ? null : round(present.stream().mapToDouble(Double::doubleValue).sum());
    }

    private Double average(List<Double> values) {
        List<Double> present = values.stream().filter(Objects::nonNull).filter(Double::isFinite).toList();
        return present.isEmpty() ? null : round(present.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
    }

    private Double percent(int count, int total) {
        return total <= 0 ? 0.0 : round(count * 100.0 / total);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private Double round(Double value) {
        return value == null || !Double.isFinite(value) ? null : Math.round(value * 1000.0) / 1000.0;
    }
}