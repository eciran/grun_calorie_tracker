package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CatalogProductPublishRequestDto(
        @NotBlank @Size(max = 500) String reason
) {
}
