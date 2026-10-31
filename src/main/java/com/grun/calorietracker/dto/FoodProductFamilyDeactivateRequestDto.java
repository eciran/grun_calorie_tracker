package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FoodProductFamilyDeactivateRequestDto(
        @NotBlank @Size(min = 10, max = 1000) String reason
) {
}
