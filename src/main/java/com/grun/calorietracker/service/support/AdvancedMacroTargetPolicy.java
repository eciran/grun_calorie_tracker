package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.AdvancedGoalPreviewDto;
import com.grun.calorietracker.dto.AdvancedGoalRequestDto;
import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.enums.GoalCalculationMode;
import com.grun.calorietracker.enums.GoalControlledStrategy;
import com.grun.calorietracker.enums.GoalType;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;

@Component
public class AdvancedMacroTargetPolicy {
    public static final String VERSION = "ADVANCED_MACRO_POLICY_V1";
    @Value("${grun.goals.advanced.min-calories:800}")
    private int minOperationalCalories = 800;
    @Value("${grun.goals.advanced.max-calories:6000}")
    private int maxOperationalCalories = 6000;
    @Value("${grun.goals.advanced.deviation-warning-ratio:0.20}")
    private double deviationWarningRatio = 0.20;

    public AdvancedGoalPreviewDto preview(AdvancedGoalRequestDto request, GoalCalculationResponse automatic) {
        if (request.getMode() == GoalCalculationMode.AUTO) {
            throw new IllegalArgumentException("Use restore automatic for AUTO mode.");
        }
        double protein;
        double carbs;
        double fat;
        if (request.getMode() == GoalCalculationMode.MANUAL) {
            requireCount(request, 3);
            protein = request.getProteinGrams();
            carbs = request.getCarbGrams();
            fat = request.getFatGrams();
        } else {
            if (request.getStrategy() == null) throw new IllegalArgumentException("Controlled strategy is required.");
            int count = count(request);
            if (request.getStrategy() == GoalControlledStrategy.PRIORITY_MACRO_FIXED && count != 1) {
                throw new IllegalArgumentException("Priority macro strategy requires exactly one macro.");
            }
            if (request.getStrategy() == GoalControlledStrategy.CALORIES_FIXED && (count < 1 || count > 2)) {
                throw new IllegalArgumentException("Calories-fixed strategy requires one or two macros.");
            }
            double[] resolved = resolveControlled(request, automatic);
            protein = resolved[0]; carbs = resolved[1]; fat = resolved[2];
        }
        protein = round(protein); carbs = round(carbs); fat = round(fat);
        int calories = (int) Math.round(protein * 4 + carbs * 4 + fat * 9);
        List<String> warnings = new ArrayList<>();
        if (request.getMode() == GoalCalculationMode.MANUAL) {
            warnings.add("MANUAL_TARGET_USER_DEFINED");
        }
        if (Math.abs(calories - automatic.getCalculatedCalorieNeed())
                > automatic.getCalculatedCalorieNeed() * deviationWarningRatio) {
            warnings.add("CALORIE_DEVIATION_HIGH");
        }
        boolean directionValid = directionValid(request.getGoalType(), calories, automatic.getMaintenanceCalories());
        if (!directionValid) warnings.add("GOAL_DIRECTION_CONFLICT");
        boolean boundsValid = calories >= minOperationalCalories && calories <= maxOperationalCalories;
        if (!boundsValid) warnings.add("CALORIE_OUTSIDE_OPERATIONAL_BOUNDS");
        boolean requiresAcknowledgement = request.getMode() == GoalCalculationMode.MANUAL
                || warnings.stream().anyMatch("CALORIE_DEVIATION_HIGH"::equals);
        return AdvancedGoalPreviewDto.builder()
                .mode(request.getMode()).strategy(request.getStrategy()).calories(calories)
                .proteinGrams(protein).carbGrams(carbs).fatGrams(fat)
                .automaticReference(automatic).warnings(List.copyOf(warnings))
                .requiresAcknowledgement(requiresAcknowledgement)
                .canSave(directionValid && boundsValid).build();
    }

    private double[] resolveControlled(AdvancedGoalRequestDto r, GoalCalculationResponse a) {
        Double p = r.getProteinGrams(), c = r.getCarbGrams(), f = r.getFatGrams();
        double remaining = a.getCalculatedCalorieNeed()
                - (p == null ? 0 : p * 4) - (c == null ? 0 : c * 4) - (f == null ? 0 : f * 9);
        if (remaining <= 0) throw new IllegalArgumentException("Locked macros exceed the calorie budget.");
        double autoP = a.getRecommendedProteinGrams() * 4.0;
        double autoC = a.getRecommendedCarbGrams() * 4.0;
        double autoF = a.getRecommendedFatGrams() * 9.0;
        double missingWeight = (p == null ? autoP : 0) + (c == null ? autoC : 0) + (f == null ? autoF : 0);
        if (p == null) p = remaining * autoP / missingWeight / 4.0;
        if (c == null) c = remaining * autoC / missingWeight / 4.0;
        if (f == null) f = remaining * autoF / missingWeight / 9.0;
        return new double[]{p, c, f};
    }

    private static boolean directionValid(GoalType type, int calories, Integer maintenance) {
        if (maintenance == null) return false;
        return switch (type) {
            case LOSE_WEIGHT -> calories < maintenance;
            case GAIN_WEIGHT, BUILD_MUSCLE -> calories > maintenance;
            case MAINTAIN_WEIGHT -> Math.abs(calories - maintenance) <= Math.max(100, maintenance * 0.10);
        };
    }

    private static int count(AdvancedGoalRequestDto r) {
        return (r.getProteinGrams() == null ? 0 : 1) + (r.getCarbGrams() == null ? 0 : 1) + (r.getFatGrams() == null ? 0 : 1);
    }
    private static void requireCount(AdvancedGoalRequestDto r, int expected) {
        if (count(r) != expected) throw new IllegalArgumentException("All three macros are required for manual mode.");
    }
    private static double round(double value) { return Math.round(value * 10.0) / 10.0; }
}
