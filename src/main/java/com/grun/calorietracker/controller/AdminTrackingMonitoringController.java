package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminTrackingSummaryDto;
import com.grun.calorietracker.service.AdminTrackingMonitoringService;
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
@RequestMapping("/api/v1/admin/tracking")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Tracking Monitoring", description = "Admin-only aggregate monitoring for water, fasting, and step tracking.")
public class AdminTrackingMonitoringController {

    private final AdminTrackingMonitoringService adminTrackingMonitoringService;

    @GetMapping("/summary")
    @Operation(
            summary = "Get aggregate tracking summary",
            description = "Returns aggregate water, fasting, and step tracking adoption/volume metrics. No user-level tracking records are returned."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tracking summary returned.", content = @Content(schema = @Schema(implementation = AdminTrackingSummaryDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.", content = @Content)
    })
    public ResponseEntity<AdminTrackingSummaryDto> getSummary(
            @RequestParam(defaultValue = "30") Integer days
    ) {
        return ResponseEntity.ok(adminTrackingMonitoringService.getSummary(days));
    }
}
