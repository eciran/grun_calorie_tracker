package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Read-only preview of the historical category-resolution cohort.")
public record FoodCategoryBackfillPreviewDto(
        @Schema(description = "Products eligible for category resolution.") long candidateProducts,
        @Schema(description = "Lowest eligible product id, or null when the cohort is empty.") Long firstProductId,
        @Schema(description = "Highest eligible product id, or null when the cohort is empty.") Long lastProductId
) {}
