package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Coaching-only projection, excluding identifiers and raw provider metadata. */
public final class AiCoachingContext {
    private AiCoachingContext() {}

    public static void append(Map<String, Object> context, DailySummaryDto day,
                              WaterDailySummaryDto water, SleepDailySummaryDto sleep) {
        context.put("consumedProteinGrams", day.getConsumedProtein());
        context.put("targetProteinGrams", day.getTargetProtein());
        context.put("consumedCarbsGrams", day.getConsumedCarbs());
        context.put("targetCarbsGrams", day.getTargetCarbs());
        context.put("consumedFatGrams", day.getConsumedFat());
        context.put("targetFatGrams", day.getTargetFat());
        context.put("currentWeightKg", day.getCurrentWeight());
        context.put("targetWeightKg", day.getTargetWeight());
        context.put("goalType", day.getGoalType());
        context.put("weightBasis", "Latest recorded weight or profile fallback; not necessarily a measurement on the analysis date");
        context.put("foodLogCount", day.getFoodLogs() == null ? null : day.getFoodLogs().size());
        context.put("mealCount", day.getFoodLogs() == null ? null : (int) day.getFoodLogs().stream()
                .map(FoodLogsDto::getMealType).filter(java.util.Objects::nonNull).distinct().count());
        context.put("mealMacros", day.getMealMacroDistribution() == null ? null : day.getMealMacroDistribution().stream().map(meal -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("meal", meal.getMealType());
            item.put("calories", meal.getCalories());
            item.put("proteinGrams", meal.getProtein());
            item.put("carbsGrams", meal.getCarbs());
            item.put("fatGrams", meal.getFat());
            return item;
        }).toList());
        context.put("waterDataAvailable", water != null);
        context.put("hasWaterLogs", water == null ? null : water.getTotalMl() != null && water.getTotalMl() > 0);
        context.put("waterMl", water == null ? null : water.getTotalMl());
        context.put("waterTargetMl", water == null ? null : water.getTargetMl());
        context.put("sleepDataAvailable", sleep != null);
        context.put("hasSleepLogs", sleep == null ? null : sleep.getSessionCount() > 0);
        context.put("sleepMinutes", sleep == null || sleep.getSessionCount() == 0 ? null : sleep.getTotalSleepMinutes());
        context.put("sleepTargetMinutes", sleep == null ? null : sleep.getTargetMinutes());
        context.put("sleepQualityScore", sleep == null ? null : sleep.getAverageQualityScore());
        var steps = day.getStepSummary();
        context.put("hasStepData", steps == null ? null : steps.getHasStepData());
        context.put("steps", steps == null || !Boolean.TRUE.equals(steps.getHasStepData()) ? null : steps.getTotalSteps());
        context.put("stepTarget", steps == null ? null : steps.getTargetSteps());
        var health = day.getHealthSummary();
        context.put("activeEnergyCalories", health == null ? null : health.getActiveEnergyCalories());
        context.put("restingEnergyCalories", health == null ? null : health.getRestingEnergyCalories());
        context.put("totalEnergyCalories", health == null ? null : health.getTotalEnergyCalories());
        context.put("exercises", day.getExerciseLogs() == null ? null : day.getExerciseLogs().stream().map(log -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", log.getExerciseItemName());
            item.put("minutes", log.getDurationMinutes());
            item.put("sets", log.getSetCount());
            item.put("reps", log.getReps());
            item.put("weightKg", log.getWeightKg());
            item.put("distanceKm", log.getDistanceKm());
            item.put("caloriesBurned", log.getCaloriesBurned());
            return item;
        }).toList());
    }
}
