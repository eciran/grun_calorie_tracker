package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.ProgressLogEntity;
import com.grun.calorietracker.entity.SleepSessionEntity;
import com.grun.calorietracker.entity.BodyMeasurementEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.GoalType;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.ProgressLogRepository;
import com.grun.calorietracker.repository.SleepSessionRepository;
import com.grun.calorietracker.repository.BodyMeasurementRepository;
import com.grun.calorietracker.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgressAnalyticsServiceImpl implements ProgressAnalyticsService {

    private static final int MAX_RANGE_DAYS = 366;
    private static final double CALORIE_TARGET_TOLERANCE = 0.10;

    private final SubscriptionService subscriptionService;
    private final UserService userService;
    private final UserGoalService userGoalService;
    private final FoodLogsService foodLogsService;
    private final ExerciseLogsService exerciseLogsService;
    private final StepTrackingService stepTrackingService;
    private final WaterTrackingService waterTrackingService;
    private final FastingTrackingService fastingTrackingService;
    private final ProgressLogRepository progressLogRepository;
    private final BodyMeasurementRepository bodyMeasurementRepository;
    private final SleepSessionRepository sleepSessionRepository;

    @Override
    @Transactional(readOnly = true)
    public ProgressAnalyticsDto getAnalytics(
            String email,
            LocalDate startDate,
            LocalDate endDate,
            boolean comparePrevious
    ) {
        validateRange(startDate, endDate);
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.ADVANCED_ANALYTICS);
        return buildAnalytics(email, startDate, endDate, comparePrevious);
    }

    @Override
    @Transactional(readOnly = true)
    public ProgressBasicAnalyticsDto getBasicAnalytics(
            String email,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateRange(startDate, endDate);
        return ProgressBasicAnalyticsDto.from(buildAnalytics(email, startDate, endDate, false));
    }

    private ProgressAnalyticsDto buildAnalytics(
            String email,
            LocalDate startDate,
            LocalDate endDate,
            boolean comparePrevious
    ) {
        UserEntity user = userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        ZoneId userZone = resolveUserZone(user.getTimeZone());
        validateNotFuture(endDate, userZone);
        UserGoalDto goal = userGoalService.getCurrentUserGoal(email);
        int dayCount = Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1);

        PeriodData current = loadPeriod(email, startDate, endDate, goal);
        LocalDate comparisonEnd = startDate.minusDays(1);
        LocalDate comparisonStart = comparisonEnd.minusDays(dayCount - 1L);
        PeriodData previous = comparePrevious
                ? loadPeriod(email, comparisonStart, comparisonEnd, goal)
                : null;

        List<ProgressLogEntity> rangeWeights = progressLogRepository
                .findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                        user, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
        List<ProgressLogEntity> trendWeights = progressLogRepository
                .findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                        user, endDate.minusDays(13).atStartOfDay(), endDate.plusDays(1).atStartOfDay());        List<ProgressLogEntity> plateauWeights = progressLogRepository
                .findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                        user, endDate.minusDays(27).atStartOfDay(), endDate.plusDays(1).atStartOfDay());
        Double currentWeight = progressLogRepository
                .findTopByUserAndLogDateLessThanOrderByLogDateDesc(user, endDate.plusDays(1).atStartOfDay())
                .map(ProgressLogEntity::getWeight)
                .orElse(endDate.equals(LocalDate.now(userZone)) ? user.getWeight() : null);
        Double goalStartWeight = resolveGoalStartWeight(user, goal, endDate);
        WeightAnalytics weight = calculateWeightAnalytics(
                rangeWeights, trendWeights, plateauWeights, currentWeight, goalStartWeight, goal, endDate);
        List<SleepSessionEntity> sleepSessions = sleepSessionRepository
                .findByUserAndSleepDateBetweenOrderByStartedAtAsc(user, startDate, endDate);
        List<BodyMeasurementEntity> bodyMeasurements = bodyMeasurementRepository
                .findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                        user, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());

        Set<LocalDate> diaryDates = union(current.foodDates(), current.exerciseDates());
        int currentStreak = currentStreak(diaryDates, endDate);
        int bestStreak = bestStreak(diaryDates);

        int stepDataDays = current.steps().getDays() == null ? 0 : (int) current.steps().getDays().stream()
                .filter(day -> Boolean.TRUE.equals(day.getHasStepData())).count();
        int waterDataDays = current.water().getDays() == null ? 0 : (int) current.water().getDays().stream()
                .filter(day -> day.getTotalMl() != null && day.getTotalMl() > 0).count();
        int fastingDays = current.fasting().getDailyTrends() == null ? 0 : (int) current.fasting().getDailyTrends().stream()
                .filter(day -> day.getSessionCount() != null && day.getSessionCount() > 0).count();
        int fastingTargetHitDays = current.fasting().getDailyTrends() == null ? 0 : (int) current.fasting().getDailyTrends().stream()
                .filter(day -> day.getTargetReachedSessionCount() != null && day.getTargetReachedSessionCount() > 0).count();

        Double averageCalories = averageCalories(current.foodByDate());
        Double averageNetCalories = averageNetCalories(current.foodByDate(), current.exerciseByDate());
        int calorieHitDays = calorieTargetHitDays(current.foodByDate(), goal);
        Double adherence = adherencePercent(calorieHitDays, current.foodByDate().size());
        String timeZone = user.getTimeZone() == null || user.getTimeZone().isBlank() ? "UTC" : user.getTimeZone();
        ComparisonResult comparison = buildComparisons(current, previous, goal);
        ProgressAnalyticsDto.ProgressSignal weightPlateau = buildWeightPlateau(plateauWeights, goal, currentWeight);
        List<ProgressAnalyticsDto.Relationship> relationships = buildRelationships(rangeWeights, current, sleepSessions);

        return ProgressAnalyticsDto.builder()
                .range(ProgressAnalyticsDto.Range.builder()
                        .startDate(startDate)
                        .endDate(endDate)
                        .dayCount(dayCount)
                        .aggregation(resolveAggregation(dayCount))
                        .timeZone(timeZone)
                        .comparisonStartDate(comparePrevious ? comparisonStart : null)
                        .comparisonEndDate(comparePrevious ? comparisonEnd : null)
                        .build())
                .dataCoverage(ProgressAnalyticsDto.DataCoverage.builder()
                        .foodLoggedDays(current.foodDates().size())
                        .exerciseDays(current.exerciseDates().size())
                        .stepDataDays(stepDataDays)
                        .waterDataDays(waterDataDays)
                        .fastingDays(fastingDays)
                        .sleepDataDays((int) sleepSessions.stream().map(SleepSessionEntity::getSleepDate).distinct().count())
                        .weightRecordCount(rangeWeights.size())
                        .bodyMeasurementRecordCount(bodyMeasurements.size())
                        .diaryDays(diaryDates.size())
                        .diaryCoveragePercent(percent(diaryDates.size(), dayCount))
                        .sufficientForTrend(dayCount >= 7 && diaryDates.size() >= 4)
                        .build())
                .overview(ProgressAnalyticsDto.Overview.builder()
                        .currentWeightKg(currentWeight)
                        .targetWeightKg(goal == null ? null : goal.getTargetWeight())
                        .rangeWeightChangeKg(weight.rangeWeightChange())
                        .averageCaloriesOnLoggedDays(averageCalories)
                        .averageExerciseAdjustedNetCaloriesOnLoggedDays(averageNetCalories)
                        .calorieTargetAdherencePercent(adherence)
                        .currentDiaryStreakDays(currentStreak)
                        .goalTrendStatus(weight.goalTrendStatus())
                        .build())
                .body(ProgressAnalyticsDto.Body.builder()
                        .goalStartWeightKg(goalStartWeight)
                        .rangeStartWeightKg(weight.rangeStartWeight())
                        .currentWeightKg(currentWeight)
                        .targetWeightKg(goal == null ? null : goal.getTargetWeight())
                        .goalProgressPercent(weight.goalProgressPercent())
                        .currentSevenDayAverageKg(weight.currentSevenDayAverage())
                        .previousSevenDayAverageKg(weight.previousSevenDayAverage())
                        .weeklyChangeKg(weight.weeklyChange())
                        .trendDirection(weight.trendDirection())
                        .projectedGoalDate(weight.projectedGoalDate())
                        .projectionStatus(weight.projectionStatus())
                        .projectionConfidence(weight.projectionConfidence())
                        .projectionSampleCount(weight.projectionSampleCount())
                        .projectionObservedDaySpan(weight.projectionObservedDaySpan())
                        .projectionWeeklySlopeKg(weight.projectionWeeklySlopeKg())
                        .weightPoints(rangeWeights.stream()
                                .map(log -> ProgressAnalyticsDto.WeightPoint.builder()
                                        .date(log.getLogDate().toLocalDate())
                                        .weightKg(round(log.getWeight()))
                                        .build())
                                .toList())
                        .build())
                .bodyComposition(buildBodyComposition(bodyMeasurements))
                .weightPlateau(weightPlateau)
                .relationships(relationships)
                .nutrition(buildNutrition(startDate, endDate, current, goal, calorieHitDays, adherence))
                .activity(ProgressAnalyticsDto.Activity.builder()
                        .exerciseSessionCount(current.exerciseLogs().size())
                        .exerciseDays(current.exerciseDates().size())
                        .totalExerciseMinutes(current.exerciseLogs().stream()
                                .map(ExerciseLogsDto::getDurationMinutes).filter(Objects::nonNull).mapToInt(Integer::intValue).sum())
                        .totalExerciseCalories(round(current.exerciseLogs().stream()
                                .map(ExerciseLogsDto::getCaloriesBurned).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum()))
                        .totalSteps(current.steps().getTotalSteps())
                        .averageSteps(current.steps().getAverageSteps())
                        .stepTargetHitDays(current.steps().getTargetHitDays())
                        .waterTargetHitDays(current.water().getTargetHitDays())
                        .averageWaterMl(current.water().getAverageMl())
                        .completedFastingSessions(current.fasting().getCompletedSessionCount())
                        .fastingTargetSuccessRate(current.fasting().getTargetSuccessRate())
                        .build())
                .habits(ProgressAnalyticsDto.Habits.builder()
                        .diaryDays(diaryDates.size())
                        .currentDiaryStreakDays(currentStreak)
                        .bestDiaryStreakDays(bestStreak)
                        .foodLoggedDays(current.foodDates().size())
                        .exerciseDays(current.exerciseDates().size())
                        .stepTargetHitDays(value(current.steps().getTargetHitDays()))
                        .waterTargetHitDays(value(current.water().getTargetHitDays()))
                        .fastingTargetHitDays(fastingTargetHitDays)
                        .build())
                .previousPeriod(buildPreviousPeriod(previous, comparisonStart, comparisonEnd, goal,
                        averageCalories, adherence, diaryDates.size()))
                .comparisons(comparison.metrics())
                .insights(comparison.insights())
                .build();
    }

    private ProgressAnalyticsDto.ProgressSignal buildWeightPlateau(
            List<ProgressLogEntity> logs,
            UserGoalDto goal,
            Double currentWeight
    ) {
        List<DailyWeight> points = dailyWeights(logs);
        int spanDays = points.size() < 2 ? 0
                : Math.toIntExact(ChronoUnit.DAYS.between(points.get(0).date(), points.get(points.size() - 1).date()));
        if (points.size() < 4 || spanDays < 14) {
            return ProgressAnalyticsDto.ProgressSignal.builder()
                    .code("WEIGHT_PLATEAU")
                    .status("INSUFFICIENT_DATA")
                    .windowDays(28)
                    .sampleCount(points.size())
                    .observedDaySpan(spanDays)
                    .build();
        }

        LocalDate origin = points.get(0).date();
        double meanX = points.stream().mapToLong(point -> ChronoUnit.DAYS.between(origin, point.date())).average().orElse(0.0);
        double meanY = points.stream().mapToDouble(DailyWeight::weightKg).average().orElse(0.0);
        double numerator = 0.0;
        double denominator = 0.0;
        for (DailyWeight point : points) {
            double x = ChronoUnit.DAYS.between(origin, point.date());
            numerator += (x - meanX) * (point.weightKg() - meanY);
            denominator += Math.pow(x - meanX, 2);
        }
        double weeklySlope = denominator == 0.0 ? 0.0 : (numerator / denominator) * 7.0;
        boolean stable = Math.abs(weeklySlope) < 0.10;
        boolean atGoal = goal != null && goal.getTargetWeight() != null && currentWeight != null
                && Math.abs(currentWeight - goal.getTargetWeight()) <= 0.3;
        boolean maintenance = goal != null && goal.getGoalType() == GoalType.MAINTAIN_WEIGHT;
        String status = stable && (atGoal || maintenance)
                ? "EXPECTED_STABILITY"
                : stable ? "DETECTED" : "NOT_DETECTED";

        return ProgressAnalyticsDto.ProgressSignal.builder()
                .code("WEIGHT_PLATEAU")
                .status(status)
                .windowDays(28)
                .sampleCount(points.size())
                .observedDaySpan(spanDays)
                .weeklySlopeKg(round(weeklySlope))
                .build();
    }

    private List<ProgressAnalyticsDto.Relationship> buildRelationships(
            List<ProgressLogEntity> weightLogs,
            PeriodData period,
            List<SleepSessionEntity> sleepSessions
    ) {
        List<DailyWeight> weights = dailyWeights(weightLogs);
        List<MetricPair> caloriePairs = new ArrayList<>();
        List<MetricPair> exercisePairs = new ArrayList<>();
        List<MetricPair> stepPairs = new ArrayList<>();
        List<MetricPair> sleepPairs = new ArrayList<>();
        Map<LocalDate, Integer> sleepMinutesByDate = sleepSessions.stream()
                .collect(Collectors.toMap(SleepSessionEntity::getSleepDate,
                        SleepSessionEntity::getDurationMinutes, Integer::sum));
        Map<LocalDate, StepDailySummaryDto> stepsByDate = period.steps().getDays() == null
                ? Map.of()
                : period.steps().getDays().stream()
                        .filter(day -> Boolean.TRUE.equals(day.getHasStepData()) && day.getTotalSteps() != null)
                        .collect(Collectors.toMap(StepDailySummaryDto::getDate, Function.identity(), (left, right) -> left));

        for (int index = 1; index < weights.size(); index++) {
            DailyWeight previous = weights.get(index - 1);
            DailyWeight current = weights.get(index);
            long intervalDays = ChronoUnit.DAYS.between(previous.date(), current.date());
            if (intervalDays < 3 || intervalDays > 35) continue;

            LocalDate intervalStart = previous.date().plusDays(1);
            LocalDate intervalEnd = current.date();
            double weeklyWeightChange = (current.weightKg() - previous.weightKg()) * 7.0 / intervalDays;
            List<FoodLogDailyStatsDto> foodDays = period.foodByDate().entrySet().stream()
                    .filter(entry -> !entry.getKey().isBefore(intervalStart) && !entry.getKey().isAfter(intervalEnd))
                    .map(Map.Entry::getValue)
                    .filter(day -> day.getTotalCalories() != null)
                    .toList();
            if (foodDays.size() >= Math.ceil(intervalDays * 0.5)) {
                double averageCalories = foodDays.stream()
                        .mapToDouble(FoodLogDailyStatsDto::getTotalCalories)
                        .average().orElse(0.0);
                caloriePairs.add(new MetricPair(averageCalories, weeklyWeightChange));
            }

            int totalExerciseMinutes = period.exerciseByDate().entrySet().stream()
                    .filter(entry -> !entry.getKey().isBefore(intervalStart) && !entry.getKey().isAfter(intervalEnd))
                    .mapToInt(entry -> entry.getValue().minutes)
                    .sum();
            exercisePairs.add(new MetricPair(totalExerciseMinutes / (double) intervalDays, weeklyWeightChange));

            List<StepDailySummaryDto> stepDays = intervalStart.datesUntil(intervalEnd.plusDays(1))
                    .map(stepsByDate::get)
                    .filter(Objects::nonNull)
                    .toList();
            if (stepDays.size() >= Math.ceil(intervalDays * 0.5)) {
                double averageSteps = stepDays.stream().mapToInt(StepDailySummaryDto::getTotalSteps).average().orElse(0.0);
                stepPairs.add(new MetricPair(averageSteps, weeklyWeightChange));
            }

            List<Integer> sleepDays = intervalStart.datesUntil(intervalEnd.plusDays(1))
                    .map(sleepMinutesByDate::get)
                    .filter(Objects::nonNull)
                    .toList();
            if (sleepDays.size() >= Math.ceil(intervalDays * 0.5)) {
                double averageSleepMinutes = sleepDays.stream().mapToInt(Integer::intValue).average().orElse(0.0);
                sleepPairs.add(new MetricPair(averageSleepMinutes, weeklyWeightChange));
            }
        }

        return List.of(
                relationship("CALORIE_INTAKE_TO_WEIGHT_CHANGE", caloriePairs),
                relationship("EXERCISE_MINUTES_TO_WEIGHT_CHANGE", exercisePairs),
                relationship("STEPS_TO_WEIGHT_CHANGE", stepPairs),
                relationship("SLEEP_DURATION_TO_WEIGHT_CHANGE", sleepPairs)
        );
    }

    private ProgressAnalyticsDto.Relationship relationship(String code, List<MetricPair> pairs) {
        Double coefficient = pearson(pairs);
        if (coefficient == null) {
            return ProgressAnalyticsDto.Relationship.builder()
                    .code(code)
                    .direction("INSUFFICIENT_DATA")
                    .strength("INSUFFICIENT_DATA")
                    .sampleSize(pairs.size())
                    .sufficientData(false)
                    .build();
        }
        double absolute = Math.abs(coefficient);
        String direction = absolute < 0.10 ? "NONE" : coefficient > 0 ? "POSITIVE" : "NEGATIVE";
        String strength = absolute < 0.30 ? "WEAK" : absolute < 0.60 ? "MODERATE" : "STRONG";
        return ProgressAnalyticsDto.Relationship.builder()
                .code(code)
                .coefficient(roundThree(coefficient))
                .direction(direction)
                .strength(strength)
                .sampleSize(pairs.size())
                .sufficientData(true)
                .build();
    }

    private Double pearson(List<MetricPair> pairs) {
        if (pairs.size() < 4) return null;
        double meanX = pairs.stream().mapToDouble(MetricPair::predictor).average().orElse(0.0);
        double meanY = pairs.stream().mapToDouble(MetricPair::outcome).average().orElse(0.0);
        double covariance = 0.0;
        double varianceX = 0.0;
        double varianceY = 0.0;
        for (MetricPair pair : pairs) {
            double x = pair.predictor() - meanX;
            double y = pair.outcome() - meanY;
            covariance += x * y;
            varianceX += x * x;
            varianceY += y * y;
        }
        if (varianceX < 0.0001 || varianceY < 0.0001) return null;
        return covariance / Math.sqrt(varianceX * varianceY);
    }

    private List<DailyWeight> dailyWeights(List<ProgressLogEntity> logs) {
        Map<LocalDate, DoubleSummaryStatistics> grouped = logs.stream()
                .filter(log -> log.getWeight() != null)
                .collect(Collectors.groupingBy(
                        log -> log.getLogDate().toLocalDate(),
                        TreeMap::new,
                        Collectors.summarizingDouble(ProgressLogEntity::getWeight)));
        return grouped.entrySet().stream()
                .map(entry -> new DailyWeight(entry.getKey(), entry.getValue().getAverage()))
                .toList();
    }

    private double roundThree(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
    private ProgressAnalyticsDto.BodyComposition buildBodyComposition(List<BodyMeasurementEntity> measurements) {
        List<ProgressAnalyticsDto.BodyMetricTrend> trends = List.of(
                bodyMetricTrend("BODY_FAT", measurements, BodyMeasurementEntity::getBodyFatPercentage, "PERCENT"),
                bodyMetricTrend("WAIST", measurements, BodyMeasurementEntity::getWaistCm, "CM"),
                bodyMetricTrend("CHEST", measurements, BodyMeasurementEntity::getChestCm, "CM"),
                bodyMetricTrend("HIP", measurements, BodyMeasurementEntity::getHipCm, "CM"),
                bodyMetricTrend("UPPER_ARM", measurements, BodyMeasurementEntity::getUpperArmCm, "CM"),
                bodyMetricTrend("THIGH", measurements, BodyMeasurementEntity::getThighCm, "CM"),
                bodyMetricTrend("NECK", measurements, BodyMeasurementEntity::getNeckCm, "CM"),
                bodyMetricTrend("SHOULDER", measurements, BodyMeasurementEntity::getShoulderCm, "CM"),
                bodyMetricTrend("FOREARM", measurements, BodyMeasurementEntity::getForearmCm, "CM"),
                bodyMetricTrend("CALF", measurements, BodyMeasurementEntity::getCalfCm, "CM"),
                bodyMetricTrend("LEFT_UPPER_ARM", measurements, BodyMeasurementEntity::getLeftUpperArmCm, "CM"),
                bodyMetricTrend("RIGHT_UPPER_ARM", measurements, BodyMeasurementEntity::getRightUpperArmCm, "CM"),
                bodyMetricTrend("LEFT_THIGH", measurements, BodyMeasurementEntity::getLeftThighCm, "CM"),
                bodyMetricTrend("RIGHT_THIGH", measurements, BodyMeasurementEntity::getRightThighCm, "CM"),
                bodyMetricTrend("LEFT_CALF", measurements, BodyMeasurementEntity::getLeftCalfCm, "CM"),
                bodyMetricTrend("RIGHT_CALF", measurements, BodyMeasurementEntity::getRightCalfCm, "CM")
        );
        int measuredDays = (int) measurements.stream()
                .map(measurement -> measurement.getRecordedAt().toLocalDate())
                .distinct()
                .count();
        int maximumSamples = trends.stream()
                .mapToInt(ProgressAnalyticsDto.BodyMetricTrend::getSampleCount)
                .max()
                .orElse(0);
        long spanDays = measurements.size() < 2 ? 0 : ChronoUnit.DAYS.between(
                measurements.get(0).getRecordedAt().toLocalDate(),
                measurements.get(measurements.size() - 1).getRecordedAt().toLocalDate());

        return ProgressAnalyticsDto.BodyComposition.builder()
                .measurementCount(measurements.size())
                .measuredDayCount(measuredDays)
                .confidence(bodyCompositionConfidence(maximumSamples, spanDays))
                .trends(trends)
                .points(measurements.stream()
                        .map(measurement -> ProgressAnalyticsDto.BodyCompositionPoint.builder()
                                .date(measurement.getRecordedAt().toLocalDate())
                                .bodyFatPercentage(roundNullable(measurement.getBodyFatPercentage()))
                                .waistCm(roundNullable(measurement.getWaistCm()))
                                .chestCm(roundNullable(measurement.getChestCm()))
                                .hipCm(roundNullable(measurement.getHipCm()))
                                .upperArmCm(roundNullable(measurement.getUpperArmCm()))
                                .thighCm(roundNullable(measurement.getThighCm()))
                                .neckCm(roundNullable(measurement.getNeckCm()))
                                .shoulderCm(roundNullable(measurement.getShoulderCm()))
                                .forearmCm(roundNullable(measurement.getForearmCm()))
                                .calfCm(roundNullable(measurement.getCalfCm()))
                                .leftUpperArmCm(roundNullable(measurement.getLeftUpperArmCm()))
                                .rightUpperArmCm(roundNullable(measurement.getRightUpperArmCm()))
                                .leftThighCm(roundNullable(measurement.getLeftThighCm()))
                                .rightThighCm(roundNullable(measurement.getRightThighCm()))
                                .leftCalfCm(roundNullable(measurement.getLeftCalfCm()))
                                .rightCalfCm(roundNullable(measurement.getRightCalfCm()))
                                .build())
                        .toList())
                .build();
    }

    private ProgressAnalyticsDto.BodyMetricTrend bodyMetricTrend(
            String code,
            List<BodyMeasurementEntity> measurements,
            Function<BodyMeasurementEntity, Double> valueExtractor,
            String unit
    ) {
        List<BodyMeasurementEntity> samples = measurements.stream()
                .filter(measurement -> valueExtractor.apply(measurement) != null)
                .toList();
        List<Double> values = samples.stream().map(valueExtractor).toList();
        long spanDays = samples.size() < 2 ? 0 : ChronoUnit.DAYS.between(
                samples.get(0).getRecordedAt().toLocalDate(),
                samples.get(samples.size() - 1).getRecordedAt().toLocalDate());
        String confidence = bodyCompositionConfidence(samples.size(), spanDays);
        if (values.size() < 2) {
            return ProgressAnalyticsDto.BodyMetricTrend.builder()
                    .code(code)
                    .latestValue(values.isEmpty() ? null : round(values.get(values.size() - 1)))
                    .direction("INSUFFICIENT_DATA")
                    .unit(unit)
                    .sampleCount(values.size())
                    .confidence(confidence)
                    .build();
        }
        double first = values.get(0);
        double latest = values.get(values.size() - 1);
        double change = latest - first;
        double stableThreshold = "PERCENT".equals(unit) ? 0.2 : 0.1;
        String direction = Math.abs(change) < stableThreshold ? "STABLE" : change > 0 ? "UP" : "DOWN";
        Double percentChange = Math.abs(first) < 0.0001 ? null : round(change / Math.abs(first) * 100.0);
        return ProgressAnalyticsDto.BodyMetricTrend.builder()
                .code(code)
                .firstValue(round(first))
                .latestValue(round(latest))
                .absoluteChange(round(change))
                .percentChange(percentChange)
                .direction(direction)
                .unit(unit)
                .sampleCount(values.size())
                .confidence(confidence)
                .build();
    }

    private String bodyCompositionConfidence(int maximumSamples, long spanDays) {
        if (maximumSamples < 2) return "INSUFFICIENT_DATA";
        if (maximumSamples >= 6 && spanDays >= 28) return "HIGH";
        if (maximumSamples >= 3 && spanDays >= 14) return "MEDIUM";
        return "LOW";
    }

    private Double roundNullable(Double value) {
        return value == null ? null : round(value);
    }
    private PeriodData loadPeriod(String email, LocalDate start, LocalDate end, UserGoalDto goal) {
        List<FoodLogDailyStatsDto> foodStats = foodLogsService.getDailyStats(
                email, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        List<ExerciseLogsDto> exerciseLogs = exerciseLogsService.getExerciseLogsHistory(
                email, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        Map<LocalDate, FoodLogDailyStatsDto> foodByDate = foodStats.stream()
                .collect(Collectors.toMap(row -> LocalDate.parse(row.getDate()), Function.identity(), (left, right) -> left, TreeMap::new));
        Map<LocalDate, ExerciseDay> exerciseByDate = new TreeMap<>();
        for (ExerciseLogsDto log : exerciseLogs) {
            LocalDate date = log.getLogDate().toLocalDate();
            ExerciseDay day = exerciseByDate.computeIfAbsent(date, ignored -> new ExerciseDay());
            day.calories += number(log.getCaloriesBurned());
            day.minutes += value(log.getDurationMinutes());
            day.sessions++;
        }
        WaterRangeSummaryDto water = subscriptionService.hasFeatureAccess(email, SubscriptionFeature.WATER_TRACKING)
                ? waterTrackingService.getRangeSummary(email, start, end)
                : emptyWaterRange(start, end);
        return new PeriodData(
                foodByDate,
                exerciseByDate,
                exerciseLogs,
                stepTrackingService.getRangeSummary(email, start, end),
                water,
                fastingTrackingService.getRangeSummary(email, start, end)
        );
    }

    private WaterRangeSummaryDto emptyWaterRange(LocalDate start, LocalDate end) {
        WaterRangeSummaryDto summary = new WaterRangeSummaryDto();
        summary.setStartDate(start);
        summary.setEndDate(end);
        summary.setTotalMl(0);
        summary.setAverageMl(null);
        summary.setBestMl(0);
        summary.setTargetHitDays(0);
        summary.setDayCount(Math.toIntExact(ChronoUnit.DAYS.between(start, end) + 1));
        summary.setDays(List.of());
        return summary;
    }

    private ProgressAnalyticsDto.Nutrition buildNutrition(
            LocalDate start,
            LocalDate end,
            PeriodData data,
            UserGoalDto goal,
            int calorieHitDays,
            Double adherence
    ) {
        List<ProgressAnalyticsDto.DailyPoint> points = start.datesUntil(end.plusDays(1))
                .map(date -> {
                    FoodLogDailyStatsDto food = data.foodByDate().get(date);
                    ExerciseDay exercise = data.exerciseByDate().get(date);
                    Double consumed = food == null ? null : round(food.getTotalCalories());
                    double burned = exercise == null ? 0.0 : round(exercise.calories);
                    return ProgressAnalyticsDto.DailyPoint.builder()
                            .date(date)
                            .consumedCalories(consumed)
                            .exerciseCalories(burned)
                            .exerciseAdjustedNetCalories(consumed == null ? null : round(consumed - burned))
                            .calorieTarget(goal == null ? null : goal.getDailyCalorieGoal())
                            .foodLogged(food != null)
                            .exerciseLogged(exercise != null)
                            .build();
                })
                .toList();
        return ProgressAnalyticsDto.Nutrition.builder()
                .calorieTarget(goal == null ? null : goal.getDailyCalorieGoal())
                .proteinTargetGrams(goal == null ? null : goal.getDailyProteinGoal())
                .carbohydrateTargetGrams(goal == null ? null : goal.getDailyCarbGoal())
                .fatTargetGrams(goal == null ? null : goal.getDailyFatGoal())
                .averageCaloriesOnLoggedDays(average(data.foodByDate(), FoodLogDailyStatsDto::getTotalCalories))
                .averageProteinGramsOnLoggedDays(average(data.foodByDate(), FoodLogDailyStatsDto::getTotalProtein))
                .averageCarbohydrateGramsOnLoggedDays(average(data.foodByDate(), FoodLogDailyStatsDto::getTotalCarbs))
                .averageFatGramsOnLoggedDays(average(data.foodByDate(), FoodLogDailyStatsDto::getTotalFat))
                .calorieTargetHitDays(calorieHitDays)
                .calorieTargetAdherencePercent(adherence)
                .dailyPoints(points)
                .build();
    }

    private ProgressAnalyticsDto.PreviousPeriod buildPreviousPeriod(
            PeriodData previous,
            LocalDate start,
            LocalDate end,
            UserGoalDto goal,
            Double currentAverage,
            Double currentAdherence,
            int currentDiaryDays
    ) {
        if (previous == null) return null;
        Double previousAverage = averageCalories(previous.foodByDate());
        int previousHits = calorieTargetHitDays(previous.foodByDate(), goal);
        Double previousAdherence = adherencePercent(previousHits, previous.foodByDate().size());
        int previousDiaryDays = union(previous.foodDates(), previous.exerciseDates()).size();
        return ProgressAnalyticsDto.PreviousPeriod.builder()
                .startDate(start)
                .endDate(end)
                .averageCaloriesOnLoggedDays(previousAverage)
                .calorieTargetAdherencePercent(previousAdherence)
                .diaryDays(previousDiaryDays)
                .averageCaloriesChange(difference(currentAverage, previousAverage))
                .calorieTargetAdherenceChangePoints(difference(currentAdherence, previousAdherence))
                .diaryDaysChange(currentDiaryDays - previousDiaryDays)
                .build();
    }

    private ComparisonResult buildComparisons(PeriodData current, PeriodData previous, UserGoalDto goal) {
        if (previous == null) return new ComparisonResult(List.of(), List.of());

        int currentDiaryDays = union(current.foodDates(), current.exerciseDates()).size();
        int previousDiaryDays = union(previous.foodDates(), previous.exerciseDates()).size();
        Double currentAdherence = adherencePercent(
                calorieTargetHitDays(current.foodByDate(), goal), current.foodByDate().size());
        Double previousAdherence = adherencePercent(
                calorieTargetHitDays(previous.foodByDate(), goal), previous.foodByDate().size());
        double currentExerciseMinutes = current.exerciseLogs().stream()
                .map(ExerciseLogsDto::getDurationMinutes).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        double previousExerciseMinutes = previous.exerciseLogs().stream()
                .map(ExerciseLogsDto::getDurationMinutes).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();

        List<ProgressAnalyticsDto.ComparisonMetric> metrics = List.of(
                comparisonMetric("CALORIE_ADHERENCE", currentAdherence, previousAdherence,
                        "PERCENTAGE_POINT", true, currentAdherence != null && previousAdherence != null),
                comparisonMetric("DIARY_DAYS", (double) currentDiaryDays, (double) previousDiaryDays,
                        "DAY", true, true),
                comparisonMetric("EXERCISE_MINUTES", currentExerciseMinutes, previousExerciseMinutes,
                        "MINUTE", true, true),
                comparisonMetric("AVERAGE_STEPS", current.steps().getAverageSteps(), previous.steps().getAverageSteps(),
                        "STEP", true, current.steps().getAverageSteps() != null && previous.steps().getAverageSteps() != null),
                comparisonMetric("WATER_TARGET_DAYS", (double) value(current.water().getTargetHitDays()),
                        (double) value(previous.water().getTargetHitDays()), "DAY", true,
                        hasWaterData(current) && hasWaterData(previous)),
                comparisonMetric("FASTING_SESSIONS", (double) value(current.fasting().getCompletedSessionCount()),
                        (double) value(previous.fasting().getCompletedSessionCount()), "SESSION", true, true)
        );

        List<ProgressAnalyticsDto.ComparisonMetric> changed = metrics.stream()
                .filter(ProgressAnalyticsDto.ComparisonMetric::isSufficientData)
                .filter(metric -> !"STABLE".equals(metric.getDirection()))
                .sorted(Comparator.comparingDouble(this::comparisonImpact).reversed())
                .limit(3)
                .toList();
        List<ProgressAnalyticsDto.Insight> insights;
        if (!changed.isEmpty()) {
            insights = changed.stream().map(this::toInsight).toList();
        } else if (metrics.stream().anyMatch(ProgressAnalyticsDto.ComparisonMetric::isSufficientData)) {
            insights = List.of(ProgressAnalyticsDto.Insight.builder()
                    .code("STABLE")
                    .tone("NEUTRAL")
                    .build());
        } else {
            insights = List.of(ProgressAnalyticsDto.Insight.builder()
                    .code("MORE_DATA_NEEDED")
                    .tone("NEUTRAL")
                    .build());
        }
        return new ComparisonResult(metrics, insights);
    }

    private ProgressAnalyticsDto.ComparisonMetric comparisonMetric(
            String code,
            Double current,
            Double previous,
            String unit,
            boolean higherIsBetter,
            boolean sufficient
    ) {
        if (!sufficient || current == null || previous == null) {
            return ProgressAnalyticsDto.ComparisonMetric.builder()
                    .code(code)
                    .unit(unit)
                    .direction("INSUFFICIENT_DATA")
                    .sufficientData(false)
                    .build();
        }
        double change = current - previous;
        Double percentChange;
        if (Math.abs(previous) < 0.0001) {
            percentChange = Math.abs(current) < 0.0001 ? 0.0 : null;
        } else {
            percentChange = round(change / Math.abs(previous) * 100.0);
        }
        double stableThreshold = "PERCENTAGE_POINT".equals(unit) ? 3.0 : 5.0;
        boolean stable = "PERCENTAGE_POINT".equals(unit)
                ? Math.abs(change) < stableThreshold
                : percentChange != null && Math.abs(percentChange) < stableThreshold;
        String direction = stable ? "STABLE" : change > 0 ? "UP" : "DOWN";
        Boolean favorable = stable ? null : Boolean.valueOf(higherIsBetter == "UP".equals(direction));
        return ProgressAnalyticsDto.ComparisonMetric.builder()
                .code(code)
                .currentValue(round(current))
                .previousValue(round(previous))
                .absoluteChange(round(change))
                .percentChange(percentChange)
                .direction(direction)
                .unit(unit)
                .favorable(favorable)
                .sufficientData(true)
                .build();
    }

    private boolean hasWaterData(PeriodData period) {
        return period.water().getDays() != null && period.water().getDays().stream()
                .anyMatch(day -> day.getTotalMl() != null && day.getTotalMl() > 0);
    }

    private double comparisonImpact(ProgressAnalyticsDto.ComparisonMetric metric) {
        if (metric.getPercentChange() != null) return Math.abs(metric.getPercentChange());
        return metric.getAbsoluteChange() == null ? 0.0 : Math.abs(metric.getAbsoluteChange());
    }

    private ProgressAnalyticsDto.Insight toInsight(ProgressAnalyticsDto.ComparisonMetric metric) {
        boolean favorable = Boolean.TRUE.equals(metric.getFavorable());
        return ProgressAnalyticsDto.Insight.builder()
                .code(favorable ? "IMPROVED" : "DECLINED")
                .metricCode(metric.getCode())
                .tone(favorable ? "POSITIVE" : "CAUTION")
                .currentValue(metric.getCurrentValue())
                .previousValue(metric.getPreviousValue())
                .change(metric.getPercentChange() != null ? metric.getPercentChange() : metric.getAbsoluteChange())
                .build();
    }

    private WeightAnalytics calculateWeightAnalytics(
            List<ProgressLogEntity> range,
            List<ProgressLogEntity> trend,
            List<ProgressLogEntity> projectionWindow,
            Double currentWeight,
            Double goalStartWeight,
            UserGoalDto goal,
            LocalDate endDate
    ) {
        Double rangeStart = range.isEmpty() ? null : range.get(0).getWeight();
        Double rangeChange = difference(currentWeight, rangeStart);
        LocalDate currentStart = endDate.minusDays(6);
        Double currentAverage = averageWeight(trend.stream()
                .filter(log -> !log.getLogDate().toLocalDate().isBefore(currentStart)).toList());
        Double previousAverage = averageWeight(trend.stream()
                .filter(log -> log.getLogDate().toLocalDate().isBefore(currentStart)).toList());
        Double weeklyChange = difference(currentAverage, previousAverage);
        String direction = weeklyChange == null ? "INSUFFICIENT_DATA"
                : Math.abs(weeklyChange) < 0.1 ? "STABLE"
                : weeklyChange > 0 ? "GAINING" : "LOSING";
        Double target = goal == null ? null : goal.getTargetWeight();
        Double progress = goalProgress(goalStartWeight, currentWeight, target, goal == null ? null : goal.getGoalType());
        String status = goalTrendStatus(currentWeight, target, weeklyChange, goal == null ? null : goal.getGoalType());
        WeightProjection projection = buildWeightProjection(
                projectionWindow, currentWeight, target, goal == null ? null : goal.getGoalType(), endDate);
        return new WeightAnalytics(rangeStart, rangeChange, currentAverage, previousAverage,
                weeklyChange, direction, progress, status, projection.projectedGoalDate(),
                projection.status(), projection.confidence(), projection.sampleCount(),
                projection.observedDaySpan(), projection.weeklySlopeKg());
    }

    private WeightProjection buildWeightProjection(
            List<ProgressLogEntity> logs,
            Double currentWeight,
            Double targetWeight,
            GoalType goalType,
            LocalDate endDate
    ) {
        List<DailyWeight> points = dailyWeights(logs);
        int spanDays = points.size() < 2 ? 0
                : Math.toIntExact(ChronoUnit.DAYS.between(points.get(0).date(), points.get(points.size() - 1).date()));
        if (currentWeight == null || targetWeight == null || goalType == null) {
            return new WeightProjection(null, "INSUFFICIENT_DATA", "INSUFFICIENT_DATA",
                    points.size(), spanDays, null);
        }
        if (Math.abs(currentWeight - targetWeight) <= 0.3) {
            return new WeightProjection(null, "AT_GOAL", "HIGH", points.size(), spanDays, null);
        }
        if (points.size() < 4 || spanDays < 14) {
            return new WeightProjection(null, "INSUFFICIENT_DATA", "INSUFFICIENT_DATA",
                    points.size(), spanDays, null);
        }

        Regression regression = linearRegression(points);
        double weeklySlope = regression.dailySlope() * 7.0;
        String confidence = projectionConfidence(points.size(), spanDays, regression.rSquared());
        if (Math.abs(weeklySlope) < 0.05) {
            return new WeightProjection(null, "STABLE", confidence, points.size(), spanDays, round(weeklySlope));
        }
        double remaining = targetWeight - currentWeight;
        if (Math.signum(remaining) != Math.signum(weeklySlope)) {
            return new WeightProjection(null, "WRONG_DIRECTION", confidence,
                    points.size(), spanDays, round(weeklySlope));
        }
        long weeks = (long) Math.ceil(Math.abs(remaining / weeklySlope));
        if (weeks > 104) {
            return new WeightProjection(null, "TOO_DISTANT", confidence,
                    points.size(), spanDays, round(weeklySlope));
        }
        return new WeightProjection(endDate.plusWeeks(Math.max(1, weeks)), "PROJECTED", confidence,
                points.size(), spanDays, round(weeklySlope));
    }

    private Regression linearRegression(List<DailyWeight> points) {
        LocalDate origin = points.get(0).date();
        double meanX = points.stream()
                .mapToLong(point -> ChronoUnit.DAYS.between(origin, point.date())).average().orElse(0.0);
        double meanY = points.stream().mapToDouble(DailyWeight::weightKg).average().orElse(0.0);
        double covariance = 0.0;
        double varianceX = 0.0;
        double varianceY = 0.0;
        for (DailyWeight point : points) {
            double xDelta = ChronoUnit.DAYS.between(origin, point.date()) - meanX;
            double yDelta = point.weightKg() - meanY;
            covariance += xDelta * yDelta;
            varianceX += xDelta * xDelta;
            varianceY += yDelta * yDelta;
        }
        double slope = varianceX == 0.0 ? 0.0 : covariance / varianceX;
        double rSquared = varianceX == 0.0 || varianceY == 0.0
                ? 0.0 : (covariance * covariance) / (varianceX * varianceY);
        return new Regression(slope, rSquared);
    }

    private String projectionConfidence(int sampleCount, int spanDays, double rSquared) {
        if (sampleCount >= 8 && spanDays >= 21 && rSquared >= 0.65) return "HIGH";
        if (sampleCount >= 5 && spanDays >= 14 && rSquared >= 0.35) return "MEDIUM";
        return "LOW";
    }

    private String resolveAggregation(int dayCount) {
        if (dayCount <= 31) return "DAY";
        if (dayCount <= 180) return "WEEK";
        return "MONTH";
    }

    private Double resolveGoalStartWeight(UserEntity user, UserGoalDto goal, LocalDate endDate) {
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        if (goal != null && goal.getCreatedAt() != null) {
            Optional<ProgressLogEntity> atGoalStart = progressLogRepository
                    .findTopByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                            user, goal.getCreatedAt(), end);
            if (atGoalStart.isPresent()) return atGoalStart.get().getWeight();
        }
        return progressLogRepository.findByUserOrderByLogDateAsc(user).stream()
                .filter(log -> log.getLogDate().isBefore(end))
                .map(ProgressLogEntity::getWeight)
                .findFirst()
                .orElse(null);
    }

    private ZoneId resolveUserZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) return ZoneId.of("UTC");
        try {
            return ZoneId.of(timeZone);
        } catch (RuntimeException ignored) {
            return ZoneId.of("UTC");
        }
    }

    private void validateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Progress analytics requires both start and end dates.");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("Progress analytics end date must not be before start date.");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Progress analytics date range must not exceed 366 days.");
        }
    }

    private void validateNotFuture(LocalDate end, ZoneId userZone) {
        if (end.isAfter(LocalDate.now(userZone))) {
            throw new IllegalArgumentException("Progress analytics end date must not be in the future.");
        }
    }

    private int calorieTargetHitDays(Map<LocalDate, FoodLogDailyStatsDto> food, UserGoalDto goal) {
        if (goal == null || goal.getDailyCalorieGoal() == null || goal.getDailyCalorieGoal() <= 0) return 0;
        double tolerance = goal.getDailyCalorieGoal() * CALORIE_TARGET_TOLERANCE;
        return (int) food.values().stream()
                .filter(day -> day.getTotalCalories() != null)
                .filter(day -> Math.abs(day.getTotalCalories() - goal.getDailyCalorieGoal()) <= tolerance)
                .count();
    }

    private Double averageCalories(Map<LocalDate, FoodLogDailyStatsDto> food) {
        return average(food, FoodLogDailyStatsDto::getTotalCalories);
    }

    private Double averageNetCalories(
            Map<LocalDate, FoodLogDailyStatsDto> food,
            Map<LocalDate, ExerciseDay> exercise
    ) {
        if (food.isEmpty()) return null;
        return round(food.entrySet().stream()
                .mapToDouble(entry -> number(entry.getValue().getTotalCalories())
                        - Optional.ofNullable(exercise.get(entry.getKey())).map(day -> day.calories).orElse(0.0))
                .average().orElse(0.0));
    }

    private Double average(Map<LocalDate, FoodLogDailyStatsDto> food, Function<FoodLogDailyStatsDto, Double> getter) {
        DoubleSummaryStatistics stats = food.values().stream()
                .map(getter).filter(Objects::nonNull).mapToDouble(Double::doubleValue).summaryStatistics();
        return stats.getCount() == 0 ? null : round(stats.getAverage());
    }

    private Double averageWeight(List<ProgressLogEntity> logs) {
        return logs.isEmpty() ? null : round(logs.stream().mapToDouble(ProgressLogEntity::getWeight).average().orElse(0.0));
    }

    private Double goalProgress(Double start, Double current, Double target, GoalType goalType) {
        if (start == null || current == null || target == null || goalType == GoalType.MAINTAIN_WEIGHT) return null;
        double denominator = target - start;
        if (Math.abs(denominator) < 0.1) return 100.0;
        return round(Math.max(0.0, Math.min(100.0, ((current - start) / denominator) * 100.0)));
    }

    private String goalTrendStatus(Double current, Double target, Double weeklyChange, GoalType goalType) {
        if (current == null || target == null || goalType == null) return "INSUFFICIENT_DATA";
        if (Math.abs(current - target) <= 0.3) return "AT_GOAL";
        if (weeklyChange == null) return "INSUFFICIENT_DATA";
        return switch (goalType) {
            case LOSE_WEIGHT -> weeklyChange < -0.1 ? "ON_TRACK" : "OFF_TRACK";
            case GAIN_WEIGHT, BUILD_MUSCLE -> weeklyChange > 0.1 ? "ON_TRACK" : "OFF_TRACK";
            case MAINTAIN_WEIGHT -> Math.abs(weeklyChange) < 0.2 ? "ON_TRACK" : "OFF_TRACK";
        };
    }


    private int currentStreak(Set<LocalDate> dates, LocalDate end) {
        int streak = 0;
        LocalDate cursor = end;
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private int bestStreak(Set<LocalDate> dates) {
        int best = 0;
        int current = 0;
        LocalDate previous = null;
        for (LocalDate date : new TreeSet<>(dates)) {
            current = previous != null && date.equals(previous.plusDays(1)) ? current + 1 : 1;
            best = Math.max(best, current);
            previous = date;
        }
        return best;
    }

    private Set<LocalDate> union(Set<LocalDate> left, Set<LocalDate> right) {
        Set<LocalDate> result = new HashSet<>(left);
        result.addAll(right);
        return result;
    }

    private Double adherencePercent(int hits, int loggedDays) {
        return loggedDays == 0 ? null : percent(hits, loggedDays);
    }

    private double percent(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : round((numerator * 100.0) / denominator);
    }

    private Double difference(Double current, Double previous) {
        return current == null || previous == null ? null : round(current - previous);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private double number(Double value) {
        return value == null ? 0.0 : value;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record PeriodData(
            Map<LocalDate, FoodLogDailyStatsDto> foodByDate,
            Map<LocalDate, ExerciseDay> exerciseByDate,
            List<ExerciseLogsDto> exerciseLogs,
            StepRangeSummaryDto steps,
            WaterRangeSummaryDto water,
            FastingRangeSummaryDto fasting
    ) {
        Set<LocalDate> foodDates() { return foodByDate.keySet(); }
        Set<LocalDate> exerciseDates() { return exerciseByDate.keySet(); }
    }

    private static final class ExerciseDay {
        private double calories;
        private int minutes;
        private int sessions;
    }

    private record DailyWeight(LocalDate date, double weightKg) { }

    private record Regression(double dailySlope, double rSquared) { }

    private record WeightProjection(
            LocalDate projectedGoalDate,
            String status,
            String confidence,
            int sampleCount,
            int observedDaySpan,
            Double weeklySlopeKg
    ) { }

    private record MetricPair(double predictor, double outcome) { }
    private record ComparisonResult(
            List<ProgressAnalyticsDto.ComparisonMetric> metrics,
            List<ProgressAnalyticsDto.Insight> insights
    ) { }

    private record WeightAnalytics(
            Double rangeStartWeight,
            Double rangeWeightChange,
            Double currentSevenDayAverage,
            Double previousSevenDayAverage,
            Double weeklyChange,
            String trendDirection,
            Double goalProgressPercent,
            String goalTrendStatus,
            LocalDate projectedGoalDate,
            String projectionStatus,
            String projectionConfidence,
            int projectionSampleCount,
            int projectionObservedDaySpan,
            Double projectionWeeklySlopeKg
    ) {
    }
}
