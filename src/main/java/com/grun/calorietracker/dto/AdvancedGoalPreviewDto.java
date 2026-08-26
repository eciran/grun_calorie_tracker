package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.GoalCalculationMode;
import com.grun.calorietracker.enums.GoalControlledStrategy;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdvancedGoalPreviewDto {
    private GoalCalculationMode mode;
    private GoalControlledStrategy strategy;
    private int calories;
    private double proteinGrams;
    private double carbGrams;
    private double fatGrams;
    private GoalCalculationResponse automaticReference;
    private List<String> warnings;
    private boolean requiresAcknowledgement;
    private boolean canSave;
    private String previewToken;
    private String profileVersion;
    private Long goalVersion;
}
