package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AnalyticsInsightTone;
import com.grun.calorietracker.enums.EnergyBalanceInsightCode;
import com.grun.calorietracker.enums.EnergyBalanceState;
import com.grun.calorietracker.enums.EnergyDataConfidence;
import com.grun.calorietracker.enums.EnergyExpenditureSource;
import com.grun.calorietracker.enums.EnergyWeightModelStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Pro-only modeled energy balance analytics for an inclusive user-local date range.")
public class EnergyBalanceAnalyticsDto {

    private Range range;
    private Summary summary;
    private WeightModel weightModel;
    private Averages averages;
    private Coverage coverage;
    private List<DailyPoint> dailyPoints;
    private Breakdown breakdown;
    private List<Insight> insights;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Range {
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer dayCount;
        private String aggregation;
        private String timeZone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private EnergyBalanceState balanceState;
        private Double totalConsumedCalories;
        private Double totalRestingEnergyCalories;
        private Double totalActiveEnergyCalories;
        private Double totalExpenditureCalories;
        private Double cumulativeBalanceCalories;
        private Double averageDailyBalanceCalories;
        private Integer deficitDays;
        private Integer surplusDays;
        private Integer balancedDays;
        private Integer evaluatedDays;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeightModel {
        private String modelCode;
        private Double energyPerKgCoefficient;
        private Double modeledWeightChangeKg;
        private Double modeledWeightChangeLowerKg;
        private Double modeledWeightChangeUpperKg;
        private Double observedWeightChangeKg;
        private Double startWeightKg;
        private LocalDate startWeightDate;
        private Double endWeightKg;
        private LocalDate endWeightDate;
        private Double differenceFromModelKg;
        private EnergyWeightModelStatus status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Averages {
        private Double consumedCaloriesOnLoggedDays;
        private Double restingEnergyCaloriesOnAvailableDays;
        private Double activeEnergyCaloriesOnAvailableDays;
        private Double expenditureCaloriesOnAvailableDays;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coverage {
        private Integer foodLoggedDays;
        private Integer restingEnergyAvailableDays;
        private Integer activeEnergyAvailableDays;
        private Integer expenditureAvailableDays;
        private Integer fullyEvaluatedDays;
        private Integer rangeDayCount;
        private Double foodCoveragePercent;
        private Double expenditureCoveragePercent;
        private Double evaluatedCoveragePercent;
        private Integer healthProviderDays;
        private Integer profileEstimateDays;
        private EnergyDataConfidence dataConfidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyPoint {
        private LocalDate date;
        private Double consumedCalories;
        private Double restingEnergyCalories;
        private Double activeEnergyCalories;
        private Double totalExpenditureCalories;
        private Double energyBalanceCalories;
        private Double cumulativeBalanceCalories;
        private EnergyBalanceState balanceState;
        private EnergyExpenditureSource expenditureSource;
        private Boolean foodLogged;
        private Boolean expenditureAvailable;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Breakdown {
        private List<MealBreakdown> meals;
        private List<ActivityBreakdown> activities;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MealBreakdown {
        private String mealType;
        private Double totalCalories;
        private Double sharePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityBreakdown {
        private String category;
        private Double totalCalories;
        private Integer durationMinutes;
        private String source;
        private Boolean includedInExpenditure;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Insight {
        private EnergyBalanceInsightCode code;
        private AnalyticsInsightTone tone;
        private Integer priority;
        private Double value;
        private String unit;
    }
}
