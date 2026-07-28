package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Advanced fasting safety eligibility result.")
public record FastingAdvancedEligibilityDto(
        boolean eligible,
        String policyVersion,
        int maximumContinuousFastingHours,
        List<String> blockingReasons
) {}
