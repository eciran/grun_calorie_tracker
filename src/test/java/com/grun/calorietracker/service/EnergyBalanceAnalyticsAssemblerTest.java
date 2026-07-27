package com.grun.calorietracker.service;

import com.grun.calorietracker.config.EnergyBalanceAnalyticsProperties;
import com.grun.calorietracker.dto.EnergyBalanceAnalyticsDto;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.entity.ExerciseLogsEntity;
import com.grun.calorietracker.entity.FoodLogsEntity;
import com.grun.calorietracker.enums.EnergyBalanceInsightCode;
import com.grun.calorietracker.enums.EnergyBalanceState;
import com.grun.calorietracker.enums.EnergyDataConfidence;
import com.grun.calorietracker.enums.EnergyExpenditureSource;
import com.grun.calorietracker.enums.EnergyWeightModelStatus;
import com.grun.calorietracker.service.support.DailyCalorieIntakeSnapshot;
import com.grun.calorietracker.service.support.DailyEnergyExpenditureSnapshot;
import com.grun.calorietracker.service.support.EnergyBalanceAnalyticsAssembler;
import com.grun.calorietracker.service.support.EnergyBalancePolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyBalanceAnalyticsAssemblerTest {

    private final EnergyBalanceAnalyticsAssembler assembler = new EnergyBalanceAnalyticsAssembler(
            new EnergyBalancePolicy(new EnergyBalanceAnalyticsProperties()));

    @Test
    void assemble_keepsMissingIntakeNullAndCalculatesCoverageFromPairedDays() {
        EnergyBalanceAnalyticsDto result = assembler.assemble(
                date(1), date(3), "Europe/Dublin",
                List.of(
                        new DailyCalorieIntakeSnapshot(date(1), 1800.0, true),
                        new DailyCalorieIntakeSnapshot(date(3), 2200.0, true)
                ),
                List.of(
                        expenditure(1, 2300.0, EnergyExpenditureSource.HEALTH_TOTAL_ENERGY),
                        expenditure(2, 2100.0, EnergyExpenditureSource.PROFILE_TDEE_ESTIMATE),
                        expenditure(3, 2000.0, EnergyExpenditureSource.HEALTH_RESTING_PLUS_ACTIVE)
                ),
                List.of(), List.of(), null
        );

        assertEquals(2, result.getCoverage().getFullyEvaluatedDays());
        assertEquals(66.667, result.getCoverage().getEvaluatedCoveragePercent());
        assertEquals(EnergyDataConfidence.LOW, result.getCoverage().getDataConfidence());
        assertNull(result.getDailyPoints().get(1).getConsumedCalories());
        assertNull(result.getDailyPoints().get(1).getEnergyBalanceCalories());
        assertNull(result.getDailyPoints().get(1).getCumulativeBalanceCalories());
        assertEquals(EnergyBalanceState.INSUFFICIENT_DATA, result.getDailyPoints().get(1).getBalanceState());
        assertEquals(-500.0, result.getDailyPoints().get(0).getCumulativeBalanceCalories());
        assertEquals(-300.0, result.getDailyPoints().get(2).getCumulativeBalanceCalories());
        assertEquals(-300.0, result.getSummary().getCumulativeBalanceCalories());
        assertEquals(1, result.getSummary().getDeficitDays());
        assertEquals(1, result.getSummary().getSurplusDays());
        assertTrue(hasInsight(result, EnergyBalanceInsightCode.ENERGY_DATA_INCOMPLETE));
    }

    @Test
    void assemble_buildsRangeBoundMealAndActivityBreakdownsWithoutDoubleCountingExercise() {
        EnergyBalanceAnalyticsDto result = assembler.assemble(
                date(1), date(3), "UTC", List.of(), List.of(),
                List.of(food("BREAKFAST", 600.0, 1), food("DINNER", 400.0, 2), food("BREAKFAST", 999.0, 4)),
                List.of(
                        exercise("Running", "manual", 30, 300.0, 1),
                        exercise("Running", "MANUAL", 20, 180.0, 2),
                        exercise("Cycling", "MANUAL", 60, 500.0, 4)
                ),
                null
        );

        assertEquals(2, result.getBreakdown().getMeals().size());
        assertEquals("BREAKFAST", result.getBreakdown().getMeals().get(0).getMealType());
        assertEquals(600.0, result.getBreakdown().getMeals().get(0).getTotalCalories());
        assertEquals(60.0, result.getBreakdown().getMeals().get(0).getSharePercent());
        assertEquals(1, result.getBreakdown().getActivities().size());
        assertEquals("Running", result.getBreakdown().getActivities().get(0).getCategory());
        assertEquals(50, result.getBreakdown().getActivities().get(0).getDurationMinutes());
        assertEquals(480.0, result.getBreakdown().getActivities().get(0).getTotalCalories());
        assertFalse(result.getBreakdown().getActivities().get(0).getIncludedInExpenditure());
    }

    @Test
    void assemble_emitsModeledAndObservedComparisonCodesWithoutNarrativeText() {
        EnergyBalanceAnalyticsDto.WeightModel weightModel = EnergyBalanceAnalyticsDto.WeightModel.builder()
                .modeledWeightChangeKg(-1.0)
                .modeledWeightChangeLowerKg(-1.15)
                .modeledWeightChangeUpperKg(-0.85)
                .observedWeightChangeKg(-1.1)
                .differenceFromModelKg(-0.1)
                .status(EnergyWeightModelStatus.AVAILABLE)
                .build();

        EnergyBalanceAnalyticsDto result = assembler.assemble(
                date(1), date(1), "UTC",
                List.of(new DailyCalorieIntakeSnapshot(date(1), 1800.0, true)),
                List.of(expenditure(1, 2200.0, EnergyExpenditureSource.HEALTH_TOTAL_ENERGY)),
                List.of(), List.of(), weightModel
        );

        assertTrue(hasInsight(result, EnergyBalanceInsightCode.ENERGY_BALANCE_DEFICIT));
        assertTrue(hasInsight(result, EnergyBalanceInsightCode.MODELED_WEIGHT_CHANGE_AVAILABLE));
        assertTrue(hasInsight(result, EnergyBalanceInsightCode.OBSERVED_CHANGE_CLOSE_TO_MODEL));
        assertFalse(hasInsight(result, EnergyBalanceInsightCode.WEIGHT_DATA_INCOMPLETE));
    }

    @Test
    void assemble_reportsInsufficientDataInsteadOfInventingZeroTotals() {
        EnergyBalanceAnalyticsDto result = assembler.assemble(
                date(1), date(2), "UTC", null, null, null, null, null);

        assertEquals(EnergyBalanceState.INSUFFICIENT_DATA, result.getSummary().getBalanceState());
        assertNull(result.getSummary().getTotalConsumedCalories());
        assertNull(result.getSummary().getTotalExpenditureCalories());
        assertNull(result.getSummary().getCumulativeBalanceCalories());
        assertEquals(EnergyDataConfidence.INSUFFICIENT, result.getCoverage().getDataConfidence());
        assertTrue(hasInsight(result, EnergyBalanceInsightCode.ENERGY_DATA_INCOMPLETE));
        assertTrue(hasInsight(result, EnergyBalanceInsightCode.WEIGHT_DATA_INCOMPLETE));
    }

    private DailyEnergyExpenditureSnapshot expenditure(int day, double total, EnergyExpenditureSource source) {
        return new DailyEnergyExpenditureSnapshot(date(day), 1500.0, total - 1500.0, total, source);
    }

    private FoodLogsEntity food(String meal, double calories, int day) {
        FoodLogsEntity log = new FoodLogsEntity();
        log.setMealType(meal);
        log.setSnapshotCalories(calories);
        log.setLogDate(LocalDateTime.of(2026, 7, day, 12, 0));
        return log;
    }

    private ExerciseLogsEntity exercise(String name, String source, int durationMinutes, double calories, int day) {
        ExerciseItemEntity item = new ExerciseItemEntity();
        item.setName(name);
        ExerciseLogsEntity log = new ExerciseLogsEntity();
        log.setExerciseItem(item);
        log.setSource(source);
        log.setDurationMinutes(durationMinutes);
        log.setCaloriesBurned(calories);
        log.setLogDate(LocalDateTime.of(2026, 7, day, 18, 0));
        return log;
    }

    private boolean hasInsight(EnergyBalanceAnalyticsDto result, EnergyBalanceInsightCode code) {
        return result.getInsights().stream().anyMatch(insight -> insight.getCode() == code);
    }

    private LocalDate date(int day) {
        return LocalDate.of(2026, 7, day);
    }
}