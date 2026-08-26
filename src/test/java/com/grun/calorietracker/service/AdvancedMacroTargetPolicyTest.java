package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdvancedGoalRequestDto;
import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.enums.GoalCalculationMode;
import com.grun.calorietracker.enums.GoalControlledStrategy;
import com.grun.calorietracker.enums.GoalType;
import com.grun.calorietracker.service.support.AdvancedMacroTargetPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdvancedMacroTargetPolicyTest {
    private final AdvancedMacroTargetPolicy policy = new AdvancedMacroTargetPolicy();

    @Test
    void manualModeDerivesCaloriesFromAllThreeMacros() {
        AdvancedGoalRequestDto request = request(GoalCalculationMode.MANUAL);
        request.setProteinGrams(150.0);
        request.setCarbGrams(200.0);
        request.setFatGrams(60.0);

        var result = policy.preview(request, automatic());

        assertEquals(1940, result.getCalories());
        assertTrue(result.isCanSave());
    }

    @Test
    void caloriesFixedCompletesTheUnspecifiedMacros() {
        AdvancedGoalRequestDto request = request(GoalCalculationMode.CONTROLLED);
        request.setStrategy(GoalControlledStrategy.CALORIES_FIXED);
        request.setProteinGrams(160.0);

        var result = policy.preview(request, automatic());

        assertEquals(2000, result.getCalories(), 1);
        assertEquals(160.0, result.getProteinGrams());
        assertTrue(result.getCarbGrams() > 0);
        assertTrue(result.getFatGrams() > 0);
    }

    @Test
    void priorityStrategyRejectsMoreThanOneLockedMacro() {
        AdvancedGoalRequestDto request = request(GoalCalculationMode.CONTROLLED);
        request.setStrategy(GoalControlledStrategy.PRIORITY_MACRO_FIXED);
        request.setProteinGrams(150.0);
        request.setFatGrams(60.0);

        assertThrows(IllegalArgumentException.class, () -> policy.preview(request, automatic()));
    }

    @Test
    void manualGoalRejectsDirectionConflict() {
        AdvancedGoalRequestDto request = request(GoalCalculationMode.MANUAL);
        request.setProteinGrams(200.0);
        request.setCarbGrams(350.0);
        request.setFatGrams(100.0);

        var result = policy.preview(request, automatic());

        assertFalse(result.isCanSave());
        assertTrue(result.getWarnings().contains("GOAL_DIRECTION_CONFLICT"));
    }

    private static AdvancedGoalRequestDto request(GoalCalculationMode mode) {
        AdvancedGoalRequestDto request = new AdvancedGoalRequestDto();
        request.setMode(mode);
        request.setGoalType(GoalType.LOSE_WEIGHT);
        return request;
    }

    private static GoalCalculationResponse automatic() {
        GoalCalculationResponse response = new GoalCalculationResponse(2000, 125, 67, 224);
        response.setMaintenanceCalories(2400);
        return response;
    }
}
