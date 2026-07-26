package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodLogDailyStatsDto;
import com.grun.calorietracker.dto.MicronutrientAnalyticsDto;
import com.grun.calorietracker.dto.MicronutrientDataQualityDto;
import com.grun.calorietracker.dto.MicronutrientTotalsDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.service.FoodLogsService;
import com.grun.calorietracker.service.MicronutrientAnalyticsService;
import com.grun.calorietracker.service.MicronutrientReferenceService;
import com.grun.calorietracker.service.SubscriptionService;
import com.grun.calorietracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MicronutrientAnalyticsServiceImpl implements MicronutrientAnalyticsService {

    private static final int MAX_RANGE_DAYS = 366;
    private static final int MINIMUM_COMPARISON_DAYS = 4;
    private static final int MAX_INSIGHTS = 5;
    private static final List<NutrientDefinition> NUTRIENTS = List.of(
            nutrient("SODIUM", "MG", "AT_MOST", FoodLogDailyStatsDto::getTotalSodium, MicronutrientTotalsDto::getSodium),
            nutrient("POTASSIUM", "MG", "AT_LEAST", FoodLogDailyStatsDto::getTotalPotassium, MicronutrientTotalsDto::getPotassium),
            nutrient("CALCIUM", "MG", "AT_LEAST", FoodLogDailyStatsDto::getTotalCalcium, MicronutrientTotalsDto::getCalcium),
            nutrient("IRON", "MG", "AT_LEAST", FoodLogDailyStatsDto::getTotalIron, MicronutrientTotalsDto::getIron),
            nutrient("MAGNESIUM", "MG", "AT_LEAST", FoodLogDailyStatsDto::getTotalMagnesium, MicronutrientTotalsDto::getMagnesium),
            nutrient("ZINC", "MG", "AT_LEAST", FoodLogDailyStatsDto::getTotalZinc, MicronutrientTotalsDto::getZinc),
            nutrient("VITAMIN_A", "MCG_RAE", "AT_LEAST", FoodLogDailyStatsDto::getTotalVitaminA, MicronutrientTotalsDto::getVitaminA),
            nutrient("VITAMIN_C", "MG", "AT_LEAST", FoodLogDailyStatsDto::getTotalVitaminC, MicronutrientTotalsDto::getVitaminC),
            nutrient("VITAMIN_D", "MCG", "AT_LEAST", FoodLogDailyStatsDto::getTotalVitaminD, MicronutrientTotalsDto::getVitaminD),
            nutrient("VITAMIN_E", "MG", "AT_LEAST", FoodLogDailyStatsDto::getTotalVitaminE, MicronutrientTotalsDto::getVitaminE),
            nutrient("VITAMIN_B12", "MCG", "AT_LEAST", FoodLogDailyStatsDto::getTotalVitaminB12, MicronutrientTotalsDto::getVitaminB12)
    );

    private final SubscriptionService subscriptionService;
    private final UserService userService;
    private final FoodLogsService foodLogsService;
    private final MicronutrientReferenceService micronutrientReferenceService;

    @Override
    @Transactional(readOnly = true)
    public MicronutrientAnalyticsDto getAnalytics(
            String email,
            LocalDate startDate,
            LocalDate endDate,
            boolean comparePrevious
    ) {
        validateRange(startDate, endDate);
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.MICRONUTRIENT_ANALYTICS);

        UserEntity user = userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        ZoneId userZone = resolveUserZone(user.getTimeZone());
        if (endDate.isAfter(LocalDate.now(userZone))) {
            throw new IllegalArgumentException("Micronutrient analytics end date must not be in the future.");
        }

        int dayCount = Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1);
        LocalDate comparisonEnd = startDate.minusDays(1);
        LocalDate comparisonStart = comparisonEnd.minusDays(dayCount - 1L);
        Map<LocalDate, FoodLogDailyStatsDto> current = loadPeriod(email, startDate, endDate);
        Map<LocalDate, FoodLogDailyStatsDto> previous = comparePrevious
                ? loadPeriod(email, comparisonStart, comparisonEnd)
                : Map.of();

        MicronutrientTotalsDto targets = micronutrientReferenceService.resolveTargets(user.getAge());
        MicronutrientDataQualityDto targetMetadata =
                micronutrientReferenceService.assessDataQuality(null, user.getAge());
        MicronutrientAnalyticsDto.Coverage coverage = buildCoverage(current, dayCount);
        List<MicronutrientAnalyticsDto.NutrientMetric> nutrients = NUTRIENTS.stream()
                .map(definition -> buildMetric(
                        definition, current, previous, targets, startDate, endDate, dayCount, comparePrevious))
                .toList();
        String dataConfidence = resolveDataConfidence(coverage);


        return MicronutrientAnalyticsDto.builder()
                .range(MicronutrientAnalyticsDto.Range.builder()
                        .startDate(startDate)
                        .endDate(endDate)
                        .dayCount(dayCount)
                        .pointGranularity("DAY")
                        .timeZone(userZone.getId())
                        .comparisonStartDate(comparePrevious ? comparisonStart : null)
                        .comparisonEndDate(comparePrevious ? comparisonEnd : null)
                        .build())
                .targetProfile(MicronutrientAnalyticsDto.TargetProfile.builder()
                        .code(targetMetadata.getTargetProfileCode())
                        .applicable(Boolean.TRUE.equals(targetMetadata.getTargetProfileApplicable()))
                        .unavailableReason(targetMetadata.getTargetProfileUnavailableReason())
                        .referenceSources(targetMetadata.getReferenceSources())
                        .build())
                .coverage(coverage)
                .summary(buildSummary(nutrients, dataConfidence))
                .insights(buildInsights(nutrients, dataConfidence))
                .nutrients(nutrients)
                .build();
    }

    private Map<LocalDate, FoodLogDailyStatsDto> loadPeriod(String email, LocalDate start, LocalDate end) {
        return foodLogsService.getDailyStats(email, start.atStartOfDay(), end.plusDays(1).atStartOfDay())
                .stream()
                .filter(row -> {
                    LocalDate date = LocalDate.parse(row.getDate());
                    return !date.isBefore(start) && !date.isAfter(end);
                })
                .collect(Collectors.toMap(
                        row -> LocalDate.parse(row.getDate()),
                        Function.identity(),
                        (left, right) -> left,
                        TreeMap::new
                ));
    }

    private MicronutrientAnalyticsDto.Coverage buildCoverage(
            Map<LocalDate, FoodLogDailyStatsDto> period,
            int dayCount
    ) {
        int availableFieldCount = period.values().stream()
                .mapToInt(this::availableNutrientCount)
                .sum();
        int completeDays = (int) period.values().stream()
                .filter(day -> availableNutrientCount(day) == NUTRIENTS.size())
                .count();
        int possibleLoggedFields = period.size() * NUTRIENTS.size();
        return MicronutrientAnalyticsDto.Coverage.builder()
                .foodLoggedDays(period.size())
                .rangeDayCount(dayCount)
                .trackedNutrientCount(NUTRIENTS.size())
                .completeMicronutrientDays(completeDays)
                .foodDiaryCoveragePercent(percent(period.size(), dayCount))
                .averageMicronutrientCoveragePercent(percent(availableFieldCount, possibleLoggedFields))
                .build();
    }

    private MicronutrientAnalyticsDto.NutrientMetric buildMetric(
            NutrientDefinition definition,
            Map<LocalDate, FoodLogDailyStatsDto> current,
            Map<LocalDate, FoodLogDailyStatsDto> previous,
            MicronutrientTotalsDto targets,
            LocalDate start,
            LocalDate end,
            int dayCount,
            boolean comparePrevious
    ) {
        Double target = targets == null ? null : definition.targetGetter().apply(targets);
        DoubleSummaryStatistics currentStats = statistics(current, definition);
        DoubleSummaryStatistics previousStats = statistics(previous, definition);
        int availableDays = Math.toIntExact(currentStats.getCount());
        Double average = availableDays == 0 ? null : round(currentStats.getAverage());
        int targetHitDays = target == null ? 0 : (int) current.values().stream()
                .map(definition.dailyGetter())
                .filter(Objects::nonNull)
                .filter(value -> targetMet(value, target, definition.referenceDirection()))
                .count();

        List<MicronutrientAnalyticsDto.TrendPoint> trend = start.datesUntil(end.plusDays(1))
                .map(date -> {
                    FoodLogDailyStatsDto day = current.get(date);
                    Double value = day == null ? null : definition.dailyGetter().apply(day);
                    return MicronutrientAnalyticsDto.TrendPoint.builder()
                            .date(date)
                            .value(value == null ? null : round(value))
                            .target(target)
                            .targetMet(value == null || target == null
                                    ? null
                                    : targetMet(value, target, definition.referenceDirection()))
                            .build();
                })
                .toList();

        return MicronutrientAnalyticsDto.NutrientMetric.builder()
                .code(definition.code())
                .unit(definition.unit())
                .target(target)
                .referenceDirection(definition.referenceDirection())
                .averageOnAvailableDays(average)
                .availableDayCount(availableDays)
                .rangeCoveragePercent(percent(availableDays, dayCount))
                .loggedDayCoveragePercent(percent(availableDays, current.size()))
                .averageTargetPercent(average == null || target == null ? null : round(average / target * 100.0))
                .targetHitDays(target == null ? null : targetHitDays)
                .targetHitRatePercent(target == null || availableDays == 0
                        ? null
                        : percent(targetHitDays, availableDays))
                .interpretation(interpret(average, target, availableDays, definition.referenceDirection()))
                .trend(trend)
                .comparison(comparePrevious
                        ? compare(currentStats, previousStats)
                        : null)
                .build();
    }

    private MicronutrientAnalyticsDto.PeriodComparison compare(
            DoubleSummaryStatistics current,
            DoubleSummaryStatistics previous
    ) {
        boolean sufficient = current.getCount() >= MINIMUM_COMPARISON_DAYS
                && previous.getCount() >= MINIMUM_COMPARISON_DAYS;
        if (!sufficient) {
            return MicronutrientAnalyticsDto.PeriodComparison.builder()
                    .currentAverage(current.getCount() == 0 ? null : round(current.getAverage()))
                    .previousAverage(previous.getCount() == 0 ? null : round(previous.getAverage()))
                    .direction("INSUFFICIENT_DATA")
                    .sufficientData(false)
                    .build();
        }
        double currentAverage = current.getAverage();
        double previousAverage = previous.getAverage();
        double change = currentAverage - previousAverage;
        Double percentChange = Math.abs(previousAverage) < 0.0001
                ? (Math.abs(currentAverage) < 0.0001 ? 0.0 : null)
                : round(change / Math.abs(previousAverage) * 100.0);
        String direction = Math.abs(change) < 0.01 ? "STABLE" : change > 0 ? "UP" : "DOWN";
        return MicronutrientAnalyticsDto.PeriodComparison.builder()
                .currentAverage(round(currentAverage))
                .previousAverage(round(previousAverage))
                .absoluteChange(round(change))
                .percentChange(percentChange)
                .direction(direction)
                .sufficientData(true)
                .build();
    }

    private DoubleSummaryStatistics statistics(
            Map<LocalDate, FoodLogDailyStatsDto> period,
            NutrientDefinition definition
    ) {
        return period.values().stream()
                .map(definition.dailyGetter())
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();
    }

    private String interpret(Double average, Double target, int availableDays, String direction) {
        if (availableDays == 0) {
            return "NO_DATA";
        }
        if (target == null) {
            return "TARGET_UNAVAILABLE";
        }
        if (availableDays < MINIMUM_COMPARISON_DAYS) {
            return "INSUFFICIENT_DATA";
        }
        if ("AT_MOST".equals(direction)) {
            return average <= target ? "WITHIN_REFERENCE" : "ABOVE_REFERENCE";
        }
        return average >= target ? "AT_OR_ABOVE_REFERENCE" : "BELOW_REFERENCE";
    }

    private MicronutrientAnalyticsDto.AnalysisSummary buildSummary(
            List<MicronutrientAnalyticsDto.NutrientMetric> nutrients,
            String dataConfidence
    ) {
        int withinReference = countInterpretations(
                nutrients, "WITHIN_REFERENCE", "AT_OR_ABOVE_REFERENCE");
        int attention = countInterpretations(nutrients, "ABOVE_REFERENCE", "BELOW_REFERENCE");
        int insufficient = countInterpretations(
                nutrients, "INSUFFICIENT_DATA", "TARGET_UNAVAILABLE");
        int noData = countInterpretations(nutrients, "NO_DATA");
        return MicronutrientAnalyticsDto.AnalysisSummary.builder()
                .dataConfidence(dataConfidence)
                .evaluatedNutrientCount(withinReference + attention)
                .withinReferenceCount(withinReference)
                .attentionNutrientCount(attention)
                .insufficientDataNutrientCount(insufficient)
                .noDataNutrientCount(noData)
                .build();
    }


    private String resolveDataConfidence(MicronutrientAnalyticsDto.Coverage coverage) {
        if (coverage.getFoodLoggedDays() >= 7
                && coverage.getFoodDiaryCoveragePercent() >= 70.0
                && coverage.getAverageMicronutrientCoveragePercent() >= 70.0) {
            return "HIGH";
        }
        if (coverage.getFoodLoggedDays() >= MINIMUM_COMPARISON_DAYS
                && coverage.getFoodDiaryCoveragePercent() >= 40.0
                && coverage.getAverageMicronutrientCoveragePercent() >= 40.0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private int countInterpretations(
            List<MicronutrientAnalyticsDto.NutrientMetric> nutrients,
            String... interpretations
    ) {
        List<String> accepted = List.of(interpretations);
        return (int) nutrients.stream()
                .map(MicronutrientAnalyticsDto.NutrientMetric::getInterpretation)
                .filter(accepted::contains)
                .count();
    }

    private double referenceDistance(MicronutrientAnalyticsDto.NutrientMetric metric) {
        return metric.getAverageTargetPercent() == null
                ? 0.0
                : Math.abs(metric.getAverageTargetPercent() - 100.0);
    }


    private MicronutrientAnalyticsDto.Insight buildNutrientInsight(
            MicronutrientAnalyticsDto.NutrientMetric metric,
            String tone,
            int priority
    ) {
        return MicronutrientAnalyticsDto.Insight.builder()
                .code("MICRONUTRIENT_" + metric.getInterpretation())
                .nutrientCode(metric.getCode())
                .tone(tone)
                .priority(priority)
                .averageTargetPercent(metric.getAverageTargetPercent())
                .trendDirection(metric.getComparison() != null && metric.getComparison().isSufficientData()
                        ? metric.getComparison().getDirection()
                        : null)
                .availableDayCount(metric.getAvailableDayCount())
                .build();
    }

    private List<MicronutrientAnalyticsDto.Insight> buildInsights(
            List<MicronutrientAnalyticsDto.NutrientMetric> nutrients,
            String dataConfidence
    ) {
        List<MicronutrientAnalyticsDto.Insight> insights = new ArrayList<>();
        nutrients.stream()
                .filter(metric -> "ABOVE_REFERENCE".equals(metric.getInterpretation())
                        || "BELOW_REFERENCE".equals(metric.getInterpretation()))
                .sorted(Comparator
                        .comparingDouble(this::referenceDistance).reversed()
                        .thenComparing(MicronutrientAnalyticsDto.NutrientMetric::getCode))
                .limit(3)
                .map(metric -> buildNutrientInsight(metric, "CAUTION", 100))
                .forEach(insights::add);

        if ("LOW".equals(dataConfidence)) {
            insights.add(MicronutrientAnalyticsDto.Insight.builder()
                    .code("MICRONUTRIENT_DATA_INCOMPLETE")
                    .tone("NEUTRAL")
                    .priority(80)
                    .build());
        }

        if (insights.stream().noneMatch(insight -> "CAUTION".equals(insight.getTone()))
                && !"LOW".equals(dataConfidence)) {
            nutrients.stream()
                    .filter(metric -> "WITHIN_REFERENCE".equals(metric.getInterpretation())
                            || "AT_OR_ABOVE_REFERENCE".equals(metric.getInterpretation()))
                    .max(Comparator
                            .comparingDouble(MicronutrientAnalyticsDto.NutrientMetric::getLoggedDayCoveragePercent)
                            .thenComparing(MicronutrientAnalyticsDto.NutrientMetric::getCode))
                    .map(metric -> buildNutrientInsight(metric, "POSITIVE", 30))
                    .ifPresent(insights::add);
        }

        return insights.stream()
                .sorted(Comparator
                        .comparingInt(MicronutrientAnalyticsDto.Insight::getPriority).reversed()
                        .thenComparing(insight -> insight.getNutrientCode() == null
                                ? ""
                                : insight.getNutrientCode()))
                .limit(MAX_INSIGHTS)
                .toList();
    }




    private int availableNutrientCount(FoodLogDailyStatsDto day) {
        return (int) NUTRIENTS.stream()
                .map(NutrientDefinition::dailyGetter)
                .map(getter -> getter.apply(day))
                .filter(Objects::nonNull)
                .count();
    }

    private boolean targetMet(double value, double target, String direction) {
        return "AT_MOST".equals(direction) ? value <= target : value >= target;
    }

    private void validateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Micronutrient analytics requires both start and end dates.");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("Micronutrient analytics end date must not be before start date.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Micronutrient analytics date range must not exceed 366 days.");
        }
    }

    private ZoneId resolveUserZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timeZone);
        } catch (RuntimeException ignored) {
            return ZoneId.of("UTC");
        }
    }

    private double percent(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : round(numerator * 100.0 / denominator);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static NutrientDefinition nutrient(
            String code,
            String unit,
            String referenceDirection,
            Function<FoodLogDailyStatsDto, Double> dailyGetter,
            Function<MicronutrientTotalsDto, Double> targetGetter
    ) {
        return new NutrientDefinition(code, unit, referenceDirection, dailyGetter, targetGetter);
    }

    private record NutrientDefinition(
            String code,
            String unit,
            String referenceDirection,
            Function<FoodLogDailyStatsDto, Double> dailyGetter,
            Function<MicronutrientTotalsDto, Double> targetGetter
    ) {
    }
}
