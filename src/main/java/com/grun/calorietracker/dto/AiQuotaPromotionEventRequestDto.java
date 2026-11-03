package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AiQuotaPromotionEventRequestDto(
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String reservationToken) { }
