package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;

public record FoodProductEvidenceResubmitRequestDto(
        @NotBlank String uploadSessionId
) {
}