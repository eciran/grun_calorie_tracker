package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@Schema(description = "Deterministic Pro progress analytics for one user-local date range.")
public class ProgressAnalyticsDto {
    private Range range;
    private DataCoverage dataCoverage;
    private Overview overview;
    private Body body;
    private BodyComposition bodyComposition;
    private ProgressSignal weightPlateau;
    private List<Relationship> relationships;
    private Nutrition nutrition;
    private Activity activity;
    private Habits habits;
    private PreviousPeriod previousPeriod;
    private List<ComparisonMetric> comparisons;
    private List<Insight> insights;

    @Data
    @Builder
    public static class Range {
        private LocalDate startDate;
        private LocalDate endDate;
        private int dayCount;
        private String aggregation;
        private String timeZone;
        private LocalDate comparisonStartDate;
        private LocalDate comparisonEndDate;
    }

    @Data
    @Builder
    public static class DataCoverage {
        private int foodLoggedDays;
        private int exerciseDays;
        private int stepDataDays;
        private int waterDataDays;
        private int fastingDays;
        private int sleepDataDays;
        private int weightRecordCount;
        private int bodyMeasurementRecordCount;
        private int diaryDays;
        private double diaryCoveragePercent;
        private boolean sufficientForTrend;
    }

    @Data
    @Builder
    public static class Overview {
        private Double currentWeightKg;
        private Double targetWeightKg;
        private Double rangeWeightChangeKg;
        private Double averageCaloriesOnLoggedDays;
        private Double averageExerciseAdjustedNetCaloriesOnLoggedDays;
        private Double calorieTargetAdherencePercent;
        private Integer currentDiaryStreakDays;
        private String goalTrendStatus;
    }

    @Data
    @Builder
    public static class Body {
        private Double goalStartWeightKg;
        private Double rangeStartWeightKg;
        private Double currentWeightKg;
        private Double targetWeightKg;
        private Double goalProgressPercent;
        private Double currentSevenDayAverageKg;
        private Double previousSevenDayAverageKg;
        private Double weeklyChangeKg;
        private String trendDirection;
        private LocalDate projectedGoalDate;
        private String projectionStatus;
        private String projectionConfidence;
        private int projectionSampleCount;
        private int projectionObservedDaySpan;
        private Double projectionWeeklySlopeKg;
        private List<WeightPoint> weightPoints;
    }

    @Data
    @Builder
    public static class WeightPoint {
        private LocalDate date;
        private double weightKg;
    }

    @Data
    @Builder
    public static class BodyComposition {
        private int measurementCount;
        private int measuredDayCount;
        private String confidence;
        private List<BodyMetricTrend> trends;
        private List<BodyCompositionPoint> points;
    }

    @Data
    @Builder
    public static class BodyMetricTrend {
        private String code;
        private Double firstValue;
        private Double latestValue;
        private Double absoluteChange;
        private Double percentChange;
        private String direction;
        private String unit;
        private int sampleCount;
        private String confidence;
    }

    @Data
    @Builder
    public static class BodyCompositionPoint {
        private LocalDate date;
        private Double bodyFatPercentage;
        private Double waistCm;
        private Double chestCm;
        private Double hipCm;
        private Double upperArmCm;
        private Double thighCm;
        private Double neckCm;
        private Double shoulderCm;
        private Double forearmCm;
        private Double calfCm;
        private Double leftUpperArmCm;
        private Double rightUpperArmCm;
        private Double leftThighCm;
        private Double rightThighCm;
        private Double leftCalfCm;
        private Double rightCalfCm;
    }

    @Data
    @Builder
    public static class ProgressSignal {
        private String code;
        private String status;
        private int windowDays;
        private int sampleCount;
        private int observedDaySpan;
        private Double weeklySlopeKg;
    }

    @Data
    @Builder
    public static class Relationship {
        private String code;
        private Double coefficient;
        private String direction;
        private String strength;
        private int sampleSize;
        private boolean sufficientData;
    }
    @Data
    @Builder
    public static class Nutrition {
        private Integer calorieTarget;
        private Double proteinTargetGrams;
        private Double carbohydrateTargetGrams;
        private Double fatTargetGrams;
        private Double averageCaloriesOnLoggedDays;
        private Double averageProteinGramsOnLoggedDays;
        private Double averageCarbohydrateGramsOnLoggedDays;
        private Double averageFatGramsOnLoggedDays;
        private Integer calorieTargetHitDays;
        private Double calorieTargetAdherencePercent;
        private List<DailyPoint> dailyPoints;
    }

    @Data
    @Builder
    public static class DailyPoint {
        private LocalDate date;
        private Double consumedCalories;
        private double exerciseCalories;
        private Double exerciseAdjustedNetCalories;
        private Integer calorieTarget;
        private boolean foodLogged;
        private boolean exerciseLogged;
    }

    @Data
    @Builder
    public static class Activity {
        private int exerciseSessionCount;
        private int exerciseDays;
        private int totalExerciseMinutes;
        private double totalExerciseCalories;
        private Integer totalSteps;
        private Double averageSteps;
        private Integer stepTargetHitDays;
        private Integer waterTargetHitDays;
        private Double averageWaterMl;
        private Integer completedFastingSessions;
        private Double fastingTargetSuccessRate;
    }

    @Data
    @Builder
    public static class Habits {
        private int diaryDays;
        private int currentDiaryStreakDays;
        private int bestDiaryStreakDays;
        private int foodLoggedDays;
        private int exerciseDays;
        private int stepTargetHitDays;
        private int waterTargetHitDays;
        private int fastingTargetHitDays;
    }

    @Data
    @Builder
    public static class ComparisonMetric {
        private String code;
        private Double currentValue;
        private Double previousValue;
        private Double absoluteChange;
        private Double percentChange;
        private String direction;
        private String unit;
        private Boolean favorable;
        private boolean sufficientData;
    }

    @Data
    @Builder
    public static class Insight {
        private String code;
        private String metricCode;
        private String tone;
        private Double currentValue;
        private Double previousValue;
        private Double change;
    }

    @Data
    @Builder
    public static class PreviousPeriod {
        private LocalDate startDate;
        private LocalDate endDate;
        private Double averageCaloriesOnLoggedDays;
        private Double calorieTargetAdherencePercent;
        private int diaryDays;
        private Double averageCaloriesChange;
        private Double calorieTargetAdherenceChangePoints;
        private Integer diaryDaysChange;
    }
}
