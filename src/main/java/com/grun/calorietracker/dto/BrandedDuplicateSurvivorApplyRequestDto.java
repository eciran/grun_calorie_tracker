package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BrandedDuplicateSurvivorApplyRequestDto(
        @NotBlank @Size(max = 300) String brandKey,
        @NotBlank @Size(max = 500) String nameKey,
        @NotNull Long survivorProductId,
        @NotBlank @Size(min = 64, max = 64) String candidateFingerprint
) {
}
