package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.*;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AiCoachingContextTest {
    @Test
    void distinguishesUnavailableFromNoRecordsAndPreservesZeroSteps() {
        var day = new DailySummaryDto();
        var context = new LinkedHashMap<String, Object>();
        AiCoachingContext.append(context, day, null, null);
        assertEquals(false, context.get("waterDataAvailable"));
        assertNull(context.get("hasWaterLogs"));
        assertNull(context.get("sleepMinutes"));
        var water = new WaterDailySummaryDto(); water.setTotalMl(0);
        var steps = new StepDailySummaryDto(); steps.setHasStepData(true); steps.setTotalSteps(0); day.setStepSummary(steps);
        AiCoachingContext.append(context, day, water, SleepDailySummaryDto.builder().sessionCount(0).build());
        assertEquals(true, context.get("waterDataAvailable"));
        assertEquals(false, context.get("hasWaterLogs"));
        assertEquals(false, context.get("hasSleepLogs"));
        assertNull(context.get("sleepMinutes"));
        assertEquals(0, context.get("steps"));
    }

    @Test
    void projectsTrainingAndMealMacrosWithoutIdentifiersOrRawMetadata() {
        var day = new DailySummaryDto();
        var exercise = new ExerciseLogsDto(); exercise.setId(1L); exercise.setExternalId("private-id");
        exercise.setExtraData("private-metadata"); exercise.setExerciseItemName("Squat");
        exercise.setSetCount(3); exercise.setReps(8); exercise.setWeightKg(40.0);
        day.setExerciseLogs(List.of(exercise));
        var meal = new MealMacroDistributionDto(); meal.setMealType("LUNCH"); meal.setProtein(25.0);
        day.setMealMacroDistribution(List.of(meal));
        var context = new LinkedHashMap<String, Object>();
        AiCoachingContext.append(context, day, null, null);
        var item = (java.util.Map<?, ?>) ((List<?>) context.get("exercises")).get(0);
        assertEquals(3, item.get("sets")); assertEquals(8, item.get("reps"));
        assertFalse(item.containsKey("id")); assertFalse(item.containsKey("extraData"));
        assertFalse(context.toString().contains("private"));
        assertEquals(25.0, ((java.util.Map<?, ?>) ((List<?>) context.get("mealMacros")).get(0)).get("proteinGrams"));
    }
}
