package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@Schema(description = "Pro micronutrient trends, target adherence, data coverage, and period comparisons.")
public class MicronutrientAnalyticsDto {

    private Range range;
    private TargetProfile targetProfile;
    private Coverage coverage;
    private AnalysisSummary summary;
    private List<Insight> insights;
    private List<NutrientMetric> nutrients;

    @Data
    @Builder
    public static class Range {
        private LocalDate startDate;
        private LocalDate endDate;
        private int dayCount;
        private String pointGranularity;
        private String timeZone;
        private LocalDate comparisonStartDate;
        private LocalDate comparisonEndDate;
    }

    @Data
    @Builder
    public static class TargetProfile {
        private String code;
        private boolean applicable;
        private String unavailableReason;
        private List<String> referenceSources;
    }

    @Data
    @Builder
    public static class Coverage {
        private int foodLoggedDays;
        private int rangeDayCount;
        private int trackedNutrientCount;
        private int completeMicronutrientDays;
        private double foodDiaryCoveragePercent;
        private double averageMicronutrientCoveragePercent;
    }

    @Data
    @Builder
    public static class AnalysisSummary {
        private String dataConfidence;
        private int evaluatedNutrientCount;
        private int withinReferenceCount;
        private int attentionNutrientCount;
        private int insufficientDataNutrientCount;
        private int noDataNutrientCount;
    }

    @Data
    @Builder
    public static class Insight {
        private String code;
        private String nutrientCode;
        private String tone;
        private int priority;
        private Double averageTargetPercent;
        private String trendDirection;
        private int availableDayCount;
    }

    @Data
    @Builder
    public static class NutrientMetric {
        private String code;
        private String unit;
        private Double target;
        private String referenceDirection;
        private Double averageOnAvailableDays;
        private int availableDayCount;
        private double rangeCoveragePercent;
        private double loggedDayCoveragePercent;
        private Double averageTargetPercent;
        private Integer targetHitDays;
        private Double targetHitRatePercent;
        private String interpretation;
        private List<TrendPoint> trend;
        private PeriodComparison comparison;
    }

    @Data
    @Builder
    public static class TrendPoint {
        private LocalDate date;
        private Double value;
        private Double target;
        private Boolean targetMet;
    }

    @Data
    @Builder
    public static class PeriodComparison {
        private Double currentAverage;
        private Double previousAverage;
        private Double absoluteChange;
        private Double percentChange;
        private String direction;
        private boolean sufficientData;
    }
}
