package com.grun.calorietracker.dto;

import jakarta.validation.constraints.*;

public record FreePromotionEventRequestDto(
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String reservationToken) { }
