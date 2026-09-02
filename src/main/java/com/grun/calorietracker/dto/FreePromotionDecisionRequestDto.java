package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FreePromotionPlacement;
import jakarta.validation.constraints.*;

public record FreePromotionDecisionRequestDto(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{8,100}") String sessionId,
        @NotNull FreePromotionPlacement placement,
        @NotNull Boolean pendingPurchase) { }
