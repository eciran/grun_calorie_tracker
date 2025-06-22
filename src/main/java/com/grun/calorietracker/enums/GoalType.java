package com.grun.calorietracker.enums;

import lombok.Getter;

@Getter
public enum GoalType {
    LOSE_WEIGHT(-500, 0.25, 0.30, 0.45),
    GAIN_WEIGHT(300, 0.20, 0.30, 0.50),
    BUILD_MUSCLE(500, 0.30, 0.25, 0.45),
    MAINTAIN_WEIGHT(0, 0.20, 0.30, 0.50);

    private final int calorieAdjustment;
    private final double proteinPercentage;
    private final double fatPercentage;
    private final double carbPercentage;

    GoalType(int calorieAdjustment, double proteinPercentage, double fatPercentage, double carbPercentage) {
        this.calorieAdjustment = calorieAdjustment;
        this.proteinPercentage = proteinPercentage;
        this.fatPercentage = fatPercentage;
        this.carbPercentage = carbPercentage;
    }

}
