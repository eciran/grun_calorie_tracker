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
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Dashboard", description = "Admin-only summary metrics for users and food catalog quality.")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final AdminGrowthAnalyticsService growthAnalyticsService;

    @GetMapping("/summary")
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
    public ResponseEntity<AdminDashboardSummaryDto> getSummary() {
        return ResponseEntity.ok(adminDashboardService.getSummary());
    }

    @GetMapping("/growth")
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
