package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin dashboard summary metrics for user and food catalog monitoring.")
public class AdminDashboardSummaryDto {

    @Schema(description = "Total number of registered users.", example = "1280")
    private long totalUsers;

    @Schema(description = "Number of standard users.", example = "1200")
    private long standardUsers;

    @Schema(description = "Number of pro users.", example = "75")
    private long proUsers;

    @Schema(description = "Number of admin users.", example = "5")
    private long adminUsers;

    @Schema(description = "Total number of food products in the local catalog.", example = "245000")
    private long totalProducts;

    @Schema(description = "Number of verified food products.", example = "180000")
    private long verifiedProducts;

    @Schema(description = "Number of raw imported food products.", example = "50000")
    private long rawImportedProducts;

    @Schema(description = "Number of food products marked as needing review.", example = "14000")
    private long needsReviewProducts;

    @Schema(description = "Number of rejected food products.", example = "1000")
    private long rejectedProducts;

    @Schema(description = "Number of products currently requiring admin review because product data or image quality is not approved.", example = "62000")
    private long reviewQueueProducts;
}
