package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.RevenueCatMonitoringChartsDto;
import com.grun.calorietracker.dto.RevenueCatMonitoringOverviewDto;
import com.grun.calorietracker.service.RevenueCatMonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/revenuecat/monitoring")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin RevenueCat Monitoring", description = "Admin-only RevenueCat overview and charts proxy without exposing RevenueCat API secrets.")
public class AdminRevenueCatMonitoringController {

    private final RevenueCatMonitoringService monitoringService;

    @GetMapping("/overview")
    @Operation(summary = "Get RevenueCat monitoring overview", description = "Returns RevenueCat overview metrics for production or local sandbox webhook-event metrics.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "RevenueCat monitoring overview returned.",
                    content = @Content(schema = @Schema(implementation = RevenueCatMonitoringOverviewDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RevenueCatMonitoringOverviewDto> getOverview(
            @RequestParam(defaultValue = "production") String environment) {
        return ResponseEntity.ok(monitoringService.getOverview(environment));
    }

    @GetMapping("/charts")
    @Operation(summary = "Get RevenueCat monitoring charts", description = "Returns selected RevenueCat chart metrics for production or sandbox webhook-event chart data. Use range=custom with startDate and endDate in yyyy-MM-dd format for a custom date window.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "RevenueCat monitoring charts returned.",
                    content = @Content(schema = @Schema(implementation = RevenueCatMonitoringChartsDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RevenueCatMonitoringChartsDto> getCharts(
            @RequestParam(defaultValue = "production") String environment,
            @RequestParam(defaultValue = "28d") String range,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ResponseEntity.ok(monitoringService.getCharts(environment, range, startDate, endDate));
    }
}
