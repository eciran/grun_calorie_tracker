package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Calculated daily calorie and macro recommendations.")
public class GoalCalculationResponse {
    @Schema(description = "Calculated daily calorie need.", example = "2300")
    private int calculatedCalorieNeed;

    @Schema(description = "Recommended daily protein amount in grams.", example = "160")
    private int recommendedProteinGrams;

    @Schema(description = "Recommended daily fat amount in grams.", example = "70")
    private int recommendedFatGrams;

    @Schema(description = "Recommended daily carbohydrate amount in grams.", example = "250")
    private int recommendedCarbGrams;

    @Schema(description = "BMR formula selected for the available profile metrics.", example = "MIFFLIN_ST_JEOR")
    private String formula;

    @Schema(description = "Basal metabolic rate before activity is applied.", example = "1730.0")
    private Double bmr;

    @Schema(description = "Estimated maintenance calories after applying activity level.", example = "2682")
    private Integer maintenanceCalories;

    @Schema(description = "Weekly change requested by the user, before direction and safety normalization.", example = "0.8")
    private Double requestedWeeklyRateKg;

    @Schema(description = "Signed weekly change used by the calculation after safety normalization.", example = "-0.8")
    private Double effectiveWeeklyRateKg;

    @Schema(description = "Estimated whole weeks required to reach the target at the effective safe rate. Null when no finite estimate applies.", example = "20")
    private Integer estimatedDurationWeeks;
    @Schema(description = "Daily calorie adjustment applied to maintenance calories.", example = "-880")
    private Integer calorieAdjustment;

    @Schema(description = "Sex-specific minimum calorie floor used by the safety model.", example = "1500")
    private Integer minimumCalorieFloor;

    @Schema(description = "Whether the requested rate or resulting target was adjusted for safety.", example = "true")
    private boolean safetyAdjusted;

    @Schema(description = "Human-readable reason for a safety adjustment. Null when no adjustment was required.")
    private String safetyWarning;

    public GoalCalculationResponse(
            int calculatedCalorieNeed,
            int recommendedProteinGrams,
            int recommendedFatGrams,
            int recommendedCarbGrams
    ) {
        this.calculatedCalorieNeed = calculatedCalorieNeed;
        this.recommendedProteinGrams = recommendedProteinGrams;
        this.recommendedFatGrams = recommendedFatGrams;
        this.recommendedCarbGrams = recommendedCarbGrams;
    }
}
