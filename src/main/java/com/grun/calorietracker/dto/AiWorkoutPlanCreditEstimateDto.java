package com.grun.calorietracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiWorkoutPlanCreditEstimateDto {
    private Integer daysPerWeek;
    private Integer minutesPerSession;
    private Integer totalPlannedMinutes;
    private Integer baseCreditCost;
    private Integer includedMinutes;
    private Integer minutesPerAdditionalCredit;
    private Integer additionalCredits;
    private Integer totalCreditCost;
}
