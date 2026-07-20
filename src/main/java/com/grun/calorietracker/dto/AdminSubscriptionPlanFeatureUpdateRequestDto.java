package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Updates whether a feature is included in a plan for new subscription periods.")
public class AdminSubscriptionPlanFeatureUpdateRequestDto {

    @NotNull
    @Schema(description = "Whether the feature should be included in this plan for new purchases/renewals.", example = "false")
    private Boolean enabled;

    @Min(1)
    @Max(50)
    @Schema(description = "AI credits consumed after a successful use. Ignored by non-AI features.", example = "1")
    private Integer aiCreditCost;

    @Schema(description = "Date when the rule becomes product policy. Defaults to today.", example = "2026-06-01")
    private LocalDate effectiveFrom;
}
