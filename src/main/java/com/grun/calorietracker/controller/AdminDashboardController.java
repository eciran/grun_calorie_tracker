package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminDashboardGrowthDto;
import com.grun.calorietracker.dto.AdminDashboardSummaryDto;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.service.AdminDashboardService;
import com.grun.calorietracker.service.AdminGrowthAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor

@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Dashboard", description = "Admin-only summary metrics for users and food catalog quality.")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final AdminGrowthAnalyticsService growthAnalyticsService;

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_DASHBOARD_READ')")
    @Operation(
            summary = "Get admin dashboard summary",
            description = "Returns high-level user counts and food catalog quality metrics for admin monitoring."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Admin dashboard summary returned.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminDashboardSummaryDto.class))
            ),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.", content = @Content)
    })
    public ResponseEntity<AdminDashboardSummaryDto> getSummary(
            org.springframework.security.core.Authentication authentication
    ) {
        AdminDashboardSummaryDto summary = adminDashboardService.getSummary();
        restrictSummaryToGrantedCategories(summary, authentication);
        return ResponseEntity.ok(summary);
    }

    private void restrictSummaryToGrantedCategories(
            AdminDashboardSummaryDto summary,
            org.springframework.security.core.Authentication authentication
    ) {
        boolean users = hasPermission(authentication, "USERS_READ");
        boolean catalog = hasPermission(authentication, "CATALOG_READ");
        boolean finance = hasPermission(authentication, "FINANCE_READ");
        boolean technical = hasPermission(authentication, "TECHNICAL_READ");
        if (!users) {
            summary.setTotalUsers(0); summary.setStandardUsers(0); summary.setProUsers(0); summary.setAdminUsers(0);
        }
        if (!catalog) {
            summary.setTotalProducts(0); summary.setVerifiedProducts(0); summary.setRawImportedProducts(0);
            summary.setNeedsReviewProducts(0); summary.setRejectedProducts(0); summary.setReviewQueueProducts(0);
            summary.setPendingRecipeApprovals(0); summary.setPendingRecipeImportCandidates(0); summary.setOpenRecipeReports(0);
            summary.setOpenProductCorrectionSuggestions(0); summary.setOpenProductQualitySuggestions(0);
        }
        if (!finance) {
            summary.setActivePlusSubscriptions(0); summary.setActiveProSubscriptions(0);
            summary.setCanceledSubscriptions(0); summary.setRefundedSubscriptions(0);
            summary.setAiQuotaExhaustedSubscriptions(0); summary.setFailedSubscriptionProviderEvents(0);
            summary.setSubscriptionProviderEventsLast24Hours(0); summary.setRefundableAiRequests(0);
        }
        if (!technical) {
            summary.setAiRequestsLast7Days(0); summary.setAiConfirmedLast7Days(0);
            summary.setAiRejectedLast7Days(0); summary.setAiFailedLast7Days(0);
            summary.setAiRejectionReasonsLast7Days(java.util.Map.of());
        }
        long catalogApprovals = summary.getReviewQueueProducts() + summary.getPendingRecipeApprovals()
                + summary.getPendingRecipeImportCandidates() + summary.getOpenRecipeReports()
                + summary.getOpenProductCorrectionSuggestions() + summary.getOpenProductQualitySuggestions();
        summary.setTotalAdminApprovalItems(catalogApprovals + summary.getRefundableAiRequests());
    }

    private boolean hasPermission(org.springframework.security.core.Authentication authentication, String permission) {
        String authority = "ADMIN_PERMISSION_" + permission;
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    @GetMapping("/growth")
    @PreAuthorize("hasAuthority('ADMIN_PERMISSION_GROWTH_READ')")
    @Operation(
            summary = "Get executive growth dashboard",
            description = "Returns comparison-ready growth KPIs, daily registration/activity trends, activation funnel, and cohort distributions without PII."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Executive growth dashboard returned."),
            @ApiResponse(responseCode = "400", description = "Date range or time zone is invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AdminDashboardGrowthDto> getGrowth(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "Europe/Dublin") String timeZone
    ) {
        return ResponseEntity.ok(growthAnalyticsService.getGrowth(from, to, timeZone));
    }
}
