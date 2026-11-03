package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AiQuotaPromotionDecisionRequestDto(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{8,100}") String sessionId,
        @NotNull Boolean pendingPurchase) { }
