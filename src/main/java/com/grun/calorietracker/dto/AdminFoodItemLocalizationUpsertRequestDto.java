package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminFoodItemLocalizationUpsertRequestDto(
        @NotBlank @Size(max = 255) String displayName,
        @Size(max = 255) String shortDisplayName,
        Boolean active
) {
}
