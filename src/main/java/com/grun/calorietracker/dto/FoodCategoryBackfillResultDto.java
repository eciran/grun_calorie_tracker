package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of resolving canonical categories for historical catalog products.")
public record FoodCategoryBackfillResultDto(
        @Schema(description = "Products evaluated by the category resolver.") long scannedProducts,
        @Schema(description = "Products that received a canonical category.") long assignedProducts,
        @Schema(description = "Products whose existing primary category was preserved.") long preservedProducts,
        @Schema(description = "Products routed to category review.") long reviewProducts,
        @Schema(description = "Stale category-review issues resolved because their products are outside the mapped cohort.") long reconciledIssues,
        @Schema(description = "Number of independently committed batches.") int processedBatches,
        @Schema(description = "Requested batch size.") int batchSize,
        @Schema(description = "Highest product id evaluated, usable as the next cursor.") long lastProcessedId
) {}
