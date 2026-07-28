package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Safety declaration used to evaluate advanced fasting eligibility. Answers are not persisted.")
public class FastingAdvancedEligibilityRequestDto {
    @NotNull private Boolean pregnantOrBreastfeeding;
    @NotNull private Boolean eatingDisorderRiskOrHistory;
    @NotNull private Boolean diabetesOrGlucoseMedication;
    @NotNull private Boolean otherClinicianManagedCondition;
    @NotNull private Boolean safetyAcknowledged;
    @NotNull @Schema(example = "FASTING_SAFETY_V1") private String acknowledgedPolicyVersion;
}
