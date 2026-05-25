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

    @Schema(description = "Number of active PLUS subscriptions.", example = "320")
    private long activePlusSubscriptions;

    @Schema(description = "Number of active PRO subscriptions.", example = "80")
    private long activeProSubscriptions;

    @Schema(description = "Number of canceled subscriptions.", example = "12")
    private long canceledSubscriptions;

    @Schema(description = "Number of refunded subscriptions.", example = "3")
    private long refundedSubscriptions;

    @Schema(description = "Number of users whose active subscription AI quota is exhausted.", example = "14")
    private long aiQuotaExhaustedSubscriptions;

    @Schema(description = "Number of failed subscription provider events requiring admin attention.", example = "2")
    private long failedSubscriptionProviderEvents;

    @Schema(description = "Number of subscription provider events received in the last 24 hours.", example = "45")
    private long subscriptionProviderEventsLast24Hours;
}
