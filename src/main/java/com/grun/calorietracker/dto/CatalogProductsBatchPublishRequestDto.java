package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CatalogProductsBatchPublishRequestDto(
        @NotEmpty @Size(max = 100) List<@NotNull Long> productIds,
        @NotBlank @Size(max = 500) String reason
) {
}
