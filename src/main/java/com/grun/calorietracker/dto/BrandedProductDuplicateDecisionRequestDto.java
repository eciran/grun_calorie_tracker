package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.BrandedDuplicateDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BrandedProductDuplicateDecisionRequestDto(
        @NotBlank @Size(max = 300) String brandKey,
        @NotBlank @Size(max = 500) String nameKey,
        @NotNull BrandedDuplicateDecision decision,
        Long survivorProductId,
        @NotBlank @Size(min = 10, max = 1000) String reason,
        @NotBlank @Size(min = 64, max = 64) String candidateFingerprint
) {
}
