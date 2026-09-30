package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrandedDuplicateSearchCollapseRequestDto(
        @NotBlank String brandKey,
        @NotBlank String nameKey,
        @NotBlank @Size(min = 64, max = 64) String candidateFingerprint,
        @NotBlank @Size(min = 10, max = 1000) String reason
) {
}
