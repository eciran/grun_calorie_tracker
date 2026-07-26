package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FoodSearchSelectionRequestDto(
        @NotNull @Schema(example = "12") Long foodItemId,
        @NotNull @Min(1) @Schema(description = "One-based rank in the returned result list.", example = "1") Integer rank
) {
}