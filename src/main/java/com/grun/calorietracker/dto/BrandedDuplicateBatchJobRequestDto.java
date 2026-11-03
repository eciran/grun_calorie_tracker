package com.grun.calorietracker.dto;

import jakarta.validation.constraints.Size;

public record BrandedDuplicateBatchJobRequestDto(
        @Size(max = 300) String query
) {
}
