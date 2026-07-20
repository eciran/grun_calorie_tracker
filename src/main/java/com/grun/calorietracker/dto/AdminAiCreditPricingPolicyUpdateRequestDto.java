package com.grun.calorietracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminAiCreditPricingPolicyUpdateRequestDto {
    @NotNull
    @Min(1)
    @Max(50)
    private Integer baseCreditCost;

    @NotNull
    @Min(0)
    @Max(10000)
    private Integer includedUnits;

    @NotNull
    @Min(1)
    @Max(10000)
    private Integer unitsPerAdditionalCredit;

    @NotNull
    @Min(0)
    @Max(50)
    private Integer contextSurcharge;

    @NotNull
    @Min(1)
    @Max(50)
    private Integer maxCreditCost;
}
